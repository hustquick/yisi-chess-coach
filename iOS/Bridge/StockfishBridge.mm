#include "StockfishBridge.h"

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
std::atomic<Engine*>    gEngineForStop{nullptr};
std::mutex              gEngineMutex;
std::string             gResult;

void setOption(const std::string& name, const std::string& value) {
    std::istringstream command("name " + name + " value " + value);
    gEngine->get_options().setoption(command);
}

std::string escapeJSON(std::string_view value) {
    std::string output;
    output.reserve(value.size() + 8);
    for (char character : value)
    {
        switch (character)
        {
        case '"': output += "\\\""; break;
        case '\\': output += "\\\\"; break;
        case '\n':
        case '\r': output += ' '; break;
        default: output += character; break;
        }
    }
    return output;
}

std::vector<std::string> splitMoves(const char *moves) {
    std::vector<std::string> output;
    if (!moves)
        return output;

    std::istringstream stream(moves);
    std::string        move;
    while (stream >> move)
        output.push_back(move);
    return output;
}

bool ensureEngine(const char *evalFile, int threads, int hashMB) {
    if (!gEngine)
    {
        Bitboards::init();
        Position::init();
        gEngine = std::make_unique<Engine>();
        gEngine->set_on_update_no_moves([](const Engine::InfoShort&) {});
        gEngine->set_on_iter([](const Engine::InfoIter&) {});
        gEngine->set_on_bestmove([](std::string_view, std::string_view) {});
        gEngine->set_on_verify_networks([](std::string_view) {});
        gEngineForStop.store(gEngine.get(), std::memory_order_release);
    }

    setOption("Threads", std::to_string(std::clamp(threads, 1, 8)));
    setOption("Hash", std::to_string(std::clamp(hashMB, 16, 256)));
    if (evalFile && *evalFile)
        setOption("EvalFile", evalFile);
    return true;
}

struct AnalysisLine {
    int         depth = 0;
    int         selDepth = 0;
    size_t      multiPV = 0;
    std::string score;
    std::string pv;
    size_t      nodes = 0;
    size_t      nps = 0;
};

// The engine keeps its update callback until the next callback is installed.
// Keeping callback storage on the heap prevents a late search update from
// touching stack variables that have already gone out of scope after a stop.
struct AnalysisState {
    std::mutex                     mutex;
    std::map<size_t, AnalysisLine> lines;
};

}  // namespace

extern "C" const char *pf_initialize(const char *eval_file, int threads, int hash_mb) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    try
    {
        ensureEngine(eval_file, threads, hash_mb);
        gResult = "ready";
    }
    catch (const std::exception& error)
    {
        gResult = "error:" + std::string(error.what());
    }
    return gResult.c_str();
}

extern "C" const char *pf_analyze(const char *fen,
                                   int         depth,
                                   int         multipv,
                                   const char *search_moves) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    try
    {
        if (!gEngine)
        {
            gResult = "{\"lines\":[],\"error\":\"engine not initialized\"}";
            return gResult.c_str();
        }

        const int safeDepth = std::clamp(depth, 1, 30);
        const int safeMultiPV = std::clamp(multipv, 1, 32);
        setOption("MultiPV", std::to_string(safeMultiPV));

        gEngine->set_position(fen ? fen : "", {});

        auto state = std::make_shared<AnalysisState>();
        gEngine->set_on_update_full([state](const Engine::InfoFull& info) {
            std::lock_guard<std::mutex> callbackLock(state->mutex);
            auto& line = state->lines[info.multiPV];
            if (info.depth < line.depth)
                return;
            line.depth = info.depth;
            line.selDepth = info.selDepth;
            line.multiPV = info.multiPV;
            line.score = UCIEngine::format_score(info.score);
            line.pv = std::string(info.pv);
            line.nodes = info.nodes;
            line.nps = info.nps;
        });

        Search::LimitsType limits;
        limits.depth = safeDepth;
        limits.startTime = now();
        limits.searchmoves = splitMoves(search_moves);
        gEngine->go(limits);
        gEngine->wait_for_search_finished();

        std::ostringstream json;
        json << "{\"lines\":[";
        bool first = true;
        {
            std::lock_guard<std::mutex> callbackLock(state->mutex);
            for (const auto& [rank, line] : state->lines)
            {
                if (line.pv.empty())
                    continue;
                if (!first)
                    json << ',';
                first = false;
                json << "{\"depth\":" << line.depth << ",\"selDepth\":" << line.selDepth
                     << ",\"multipv\":" << rank << ",\"score\":\""
                     << escapeJSON(line.score) << "\",\"pv\":\"" << escapeJSON(line.pv)
                     << "\",\"nodes\":" << line.nodes << ",\"nps\":" << line.nps << '}';
            }
        }
        json << "],\"error\":null}";
        gResult = json.str();
    }
    catch (const std::exception& error)
    {
        gResult = "{\"lines\":[],\"error\":\"" + escapeJSON(error.what()) + "\"}";
    }
    return gResult.c_str();
}

extern "C" const char *pf_legal_moves(const char *fen) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    try
    {
        if (!gEngine)
        {
            gResult = "error:engine not initialized";
            return gResult.c_str();
        }

        StateInfo state;
        Position  position;
        position.set(fen ? fen : "", false, &state);

        std::ostringstream output;
        bool first = true;
        for (Move move : MoveList<LEGAL>(position))
        {
            if (!first)
                output << ' ';
            first = false;
            output << UCIEngine::move(move, false);
        }
        gResult = output.str();
    }
    catch (const std::exception& error)
    {
        gResult = "error:" + std::string(error.what());
    }
    return gResult.c_str();
}

extern "C" const char *pf_apply_move(const char *fen, const char *uci_move) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    try
    {
        if (!gEngine)
        {
            gResult = "error:engine not initialized";
            return gResult.c_str();
        }

        StateInfo currentState;
        StateInfo nextState;
        Position  position;
        position.set(fen ? fen : "", false, &currentState);

        const Move move = UCIEngine::to_move(position, uci_move ? uci_move : "");
        if (move == Move::none())
        {
            gResult = "error:illegal move";
            return gResult.c_str();
        }

        position.do_move(move, nextState);
        gResult = position.fen();
    }
    catch (const std::exception& error)
    {
        gResult = "error:" + std::string(error.what());
    }
    return gResult.c_str();
}

extern "C" void pf_stop(void) {
    // Engine::stop() is explicitly designed to be called while the search thread is active.
    // The singleton is initialized once and is never replaced during the app lifetime.
    if (Engine *engine = gEngineForStop.load(std::memory_order_acquire))
        engine->stop();
}
