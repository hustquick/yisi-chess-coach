#include <jni.h>

#include <algorithm>
#include <atomic>
#include <map>
#include <memory>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>

#include "bitboard.h"
#include "engine.h"
#include "movegen.h"
#include "position.h"
#include "search.h"
#include "uci.h"

using namespace Stockfish;

namespace {

std::unique_ptr<Engine> gEngine;
std::atomic<Engine*> gEngineForStop{nullptr};
std::mutex gEngineMutex;

std::string fromJava(JNIEnv* env, jstring value) {
    if (!value) return {};
    const char* raw = env->GetStringUTFChars(value, nullptr);
    std::string result = raw ? raw : "";
    if (raw) env->ReleaseStringUTFChars(value, raw);
    return result;
}

jstring toJava(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

void setOption(const std::string& name, const std::string& value) {
    std::istringstream command("name " + name + " value " + value);
    gEngine->get_options().setoption(command);
}

std::string escapeJson(std::string_view value) {
    std::string output;
    output.reserve(value.size() + 8);
    for (char character : value) {
        switch (character) {
            case '"': output += "\\\""; break;
            case '\\': output += "\\\\"; break;
            case '\n': case '\r': output += ' '; break;
            default: output += character; break;
        }
    }
    return output;
}

std::vector<std::string> splitMoves(const std::string& moves) {
    std::vector<std::string> output;
    std::istringstream stream(moves);
    std::string move;
    while (stream >> move) output.push_back(move);
    return output;
}

void ensureEngine(const std::string& evalFile, int threads, int hashMb) {
    if (!gEngine) {
        Bitboards::init();
        Position::init();
        gEngine = std::make_unique<Engine>();
        gEngine->set_on_update_no_moves([](const Engine::InfoShort&) {});
        gEngine->set_on_iter([](const Engine::InfoIter&) {});
        gEngine->set_on_bestmove([](std::string_view, std::string_view) {});
        gEngine->set_on_verify_networks([](std::string_view) {});
        gEngineForStop.store(gEngine.get(), std::memory_order_release);
    }
    setOption("Threads", std::to_string(std::clamp(threads, 1, 6)));
    setOption("Hash", std::to_string(std::clamp(hashMb, 16, 128)));
    if (!evalFile.empty()) setOption("EvalFile", evalFile);
}

struct AnalysisLine {
    int depth = 0;
    int selDepth = 0;
    size_t multipv = 0;
    std::string score;
    std::string pv;
    size_t nodes = 0;
    size_t nps = 0;
};

struct AnalysisState {
    std::map<size_t, AnalysisLine> lines;
    std::mutex mutex;
};

std::string analyzePosition(const std::string& fen, int depth, int multipv,
                            const std::string& searchMoves) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    try {
        if (!gEngine) return "{\"lines\":[],\"error\":\"engine not initialized\"}";
        int safeDepth = std::clamp(depth, 1, 30);
        int safeMultipv = std::clamp(multipv, 1, 32);
        setOption("UCI_LimitStrength", "false");
        setOption("MultiPV", std::to_string(safeMultipv));
        gEngine->set_position(fen, {});

        auto state = std::make_shared<AnalysisState>();
        gEngine->set_on_update_full([state](const Engine::InfoFull& info) {
            std::lock_guard<std::mutex> callbackLock(state->mutex);
            auto& line = state->lines[info.multiPV];
            if (info.depth < line.depth) return;
            line.depth = info.depth;
            line.selDepth = info.selDepth;
            line.multipv = info.multiPV;
            line.score = UCIEngine::format_score(info.score);
            line.pv = std::string(info.pv);
            line.nodes = info.nodes;
            line.nps = info.nps;
        });

        Search::LimitsType limits;
        limits.depth = safeDepth;
        limits.startTime = now();
        limits.searchmoves = splitMoves(searchMoves);
        gEngine->go(limits);
        gEngine->wait_for_search_finished();

        std::ostringstream json;
        json << "{\"lines\":[";
        bool first = true;
        std::lock_guard<std::mutex> stateLock(state->mutex);
        for (const auto& [rank, line] : state->lines) {
            if (line.pv.empty()) continue;
            if (!first) json << ',';
            first = false;
            json << "{\"depth\":" << line.depth
                 << ",\"selDepth\":" << line.selDepth
                 << ",\"multipv\":" << rank
                 << ",\"score\":\"" << escapeJson(line.score)
                 << "\",\"pv\":\"" << escapeJson(line.pv)
                 << "\",\"nodes\":" << line.nodes
                 << ",\"nps\":" << line.nps << '}';
        }
        json << "],\"error\":null}";
        gEngine->set_on_update_full([](const Engine::InfoFull&) {});
        return json.str();
    } catch (const std::exception& error) {
        return "{\"lines\":[],\"error\":\"" + escapeJson(error.what()) + "\"}";
    }
}

std::string bestMoveFor(const std::string& fen, int depth, int elo) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    try {
        if (!gEngine) return "error:engine not initialized";
        gEngine->set_on_update_full([](const Engine::InfoFull&) {});
        setOption("UCI_LimitStrength", "true");
        setOption("UCI_Elo", std::to_string(std::clamp(elo, Search::Skill::LowestElo,
                                                       Search::Skill::HighestElo)));
        setOption("MultiPV", "1");
        gEngine->set_position(fen, {});
        auto bestMove = std::make_shared<std::string>();
        gEngine->set_on_bestmove([bestMove](std::string_view move, std::string_view) {
            *bestMove = std::string(move);
        });
        Search::LimitsType limits;
        limits.depth = std::clamp(depth, 1, 30);
        limits.movetime = std::clamp(450 + (elo - 1320) / 2, 450, 1200);
        limits.startTime = now();
        gEngine->go(limits);
        gEngine->wait_for_search_finished();
        setOption("UCI_LimitStrength", "false");
        gEngine->set_on_bestmove([](std::string_view, std::string_view) {});
        return bestMove->empty() ? "error:no legal move" : *bestMove;
    } catch (const std::exception& error) {
        if (gEngine) setOption("UCI_LimitStrength", "false");
        return "error:" + std::string(error.what());
    }
}

std::string legalMovesFor(const std::string& fen) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    try {
        if (!gEngine) return "error:engine not initialized";
        StateInfo state;
        Position position;
        position.set(fen, false, &state);
        std::ostringstream output;
        bool first = true;
        for (Move move : MoveList<LEGAL>(position)) {
            if (!first) output << ' ';
            first = false;
            output << UCIEngine::move(move, false);
        }
        return output.str();
    } catch (const std::exception& error) {
        return "error:" + std::string(error.what());
    }
}

std::string gameStatusFor(const std::string& fen) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    try {
        StateInfo state;
        Position position;
        position.set(fen, false, &state);
        if (MoveList<LEGAL>(position).size() != 0) return "ongoing";
        return position.checkers() ? "checkmate" : "stalemate";
    } catch (const std::exception& error) {
        return "error:" + std::string(error.what());
    }
}

char sanPieceLetter(PieceType type) {
    switch (type) {
        case KING: return 'K';
        case QUEEN: return 'Q';
        case ROOK: return 'R';
        case BISHOP: return 'B';
        case KNIGHT: return 'N';
        default: return '\0';
    }
}

std::string sanFor(const std::string& fen, const std::string& uciMove) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    try {
        StateInfo currentState;
        StateInfo nextState;
        Position position;
        position.set(fen, false, &currentState);
        const Move move = UCIEngine::to_move(position, uciMove);
        if (move == Move::none()) return "error:illegal move";

        const std::string canonical = UCIEngine::move(move, false);
        const std::string from = canonical.substr(0, 2);
        const std::string to = canonical.substr(2, 2);
        const PieceType type = type_of(position.moved_piece(move));
        std::string notation;
        if (move.type_of() == CASTLING) {
            notation = to[0] == 'g' ? "O-O" : "O-O-O";
        } else {
            const bool capture = position.capture(move);
            if (type == PAWN) {
                if (capture) notation += from[0];
            } else {
                notation += sanPieceLetter(type);
                bool ambiguous = false;
                bool sameFile = false;
                bool sameRank = false;
                for (const Move candidate : MoveList<LEGAL>(position)) {
                    if (candidate == move || candidate.to_sq() != move.to_sq()
                        || type_of(position.moved_piece(candidate)) != type)
                        continue;
                    ambiguous = true;
                    const std::string candidateFrom = UCIEngine::square(candidate.from_sq());
                    sameFile = sameFile || candidateFrom[0] == from[0];
                    sameRank = sameRank || candidateFrom[1] == from[1];
                }
                if (ambiguous) {
                    if (!sameFile) notation += from[0];
                    else if (!sameRank) notation += from[1];
                    else notation += from;
                }
            }
            if (capture) notation += 'x';
            notation += to;
            if (move.type_of() == PROMOTION) {
                notation += '=';
                notation += sanPieceLetter(move.promotion_type());
            }
        }

        const bool check = position.gives_check(move);
        position.do_move(move, nextState);
        if (check) notation += MoveList<LEGAL>(position).size() == 0 ? '#' : '+';
        return notation;
    } catch (const std::exception& error) {
        return "error:" + std::string(error.what());
    }
}

std::string applyMoveTo(const std::string& fen, const std::string& uciMove) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    try {
        if (!gEngine) return "error:engine not initialized";
        StateInfo currentState;
        StateInfo nextState;
        Position position;
        position.set(fen, false, &currentState);
        Move move = UCIEngine::to_move(position, uciMove);
        if (move == Move::none()) return "error:illegal move";
        position.do_move(move, nextState);
        return position.fen();
    } catch (const std::exception& error) {
        return "error:" + std::string(error.what());
    }
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_yisi_chesscoach_StockfishNative_initialize(
        JNIEnv* env, jclass, jstring evalFile, jint threads, jint hashMb) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    try {
        ensureEngine(fromJava(env, evalFile), threads, hashMb);
        return toJava(env, "ready");
    } catch (const std::exception& error) {
        return toJava(env, "error:" + std::string(error.what()));
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_yisi_chesscoach_StockfishNative_analyze(
        JNIEnv* env, jclass, jstring fen, jint depth, jint multipv, jstring searchMoves) {
    return toJava(env, analyzePosition(fromJava(env, fen), depth, multipv, fromJava(env, searchMoves)));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_yisi_chesscoach_StockfishNative_bestMove(
        JNIEnv* env, jclass, jstring fen, jint depth, jint elo) {
    return toJava(env, bestMoveFor(fromJava(env, fen), depth, elo));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_yisi_chesscoach_StockfishNative_legalMoves(
        JNIEnv* env, jclass, jstring fen) {
    return toJava(env, legalMovesFor(fromJava(env, fen)));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_yisi_chesscoach_StockfishNative_gameStatus(
        JNIEnv* env, jclass, jstring fen) {
    return toJava(env, gameStatusFor(fromJava(env, fen)));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_yisi_chesscoach_StockfishNative_applyMove(
        JNIEnv* env, jclass, jstring fen, jstring move) {
    return toJava(env, applyMoveTo(fromJava(env, fen), fromJava(env, move)));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_yisi_chesscoach_StockfishNative_san(
        JNIEnv* env, jclass, jstring fen, jstring move) {
    return toJava(env, sanFor(fromJava(env, fen), fromJava(env, move)));
}

extern "C" JNIEXPORT void JNICALL
Java_com_yisi_chesscoach_StockfishNative_stop(JNIEnv*, jclass) {
    if (Engine* engine = gEngineForStop.load(std::memory_order_acquire)) engine->stop();
}
