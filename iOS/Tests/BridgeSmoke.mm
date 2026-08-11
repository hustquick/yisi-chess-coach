#include <cstdlib>
#include <iostream>
#include <string>

#include "StockfishBridge.h"

namespace {
constexpr const char *InitialFEN =
  "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

void require(bool condition, const std::string& message) {
    if (!condition)
    {
        std::cerr << "FAIL: " << message << '\n';
        std::exit(1);
    }
}
}  // namespace

int main(int argc, char **argv) {
    require(argc == 2, "pass the local stockfish.nnue path");

    const std::string initialized = pf_initialize(argv[1], 2, 32);
    require(initialized == "ready", initialized);

    const std::string legal = pf_legal_moves(InitialFEN);
    require(legal.find("e2e4") != std::string::npos, "initial position must allow e2-e4");
    require(legal.find("g1f3") != std::string::npos, "initial position must allow Ng1-f3");
    require(legal.find("e1e3") == std::string::npos, "king cannot jump over its own pawn");

    const std::string after = pf_apply_move(InitialFEN, "e2e4");
    require(after.find(" b ") != std::string::npos, "side to move must change after a legal move");
    require(std::string(pf_apply_move(InitialFEN, "e1e3")).find("error:illegal move") == 0,
            "illegal moves must be rejected by Stockfish");

    const std::string analysis = pf_analyze(InitialFEN, 4, 3, "");
    require(analysis.find("\"lines\":[{") != std::string::npos, "analysis must contain engine lines");
    require(analysis.find("\"depth\":4") != std::string::npos, "analysis must reach requested depth");
    require(analysis.find("\"score\":\"") != std::string::npos, "analysis must contain scores");

    std::cout << "PASS: legal moves, move application, and local NNUE analysis are working.\n";
    return 0;
}
