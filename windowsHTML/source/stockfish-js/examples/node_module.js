#!/usr/bin/env node

/// This is an example of how to directly require() the stockfish.js repo.
///NOTE: Install with `npm i stockfish`.

/// Make sure the engine is present.
require("./get-engine.js");

var enginePath = process.argv[2];

///NOTE: enginePath can be a path to any stockfish.js engine or a keyword indicating which Stockfish.js engine to load.
///      Keywords include: "full", "lite", "single", "lite-single", and "asm".
///      enginePath is optional. If not passed, the full engine will be used.
///      
///      If no callback function is passed in, this function will return a Promise which could be used for chaining or async/await calling.
var stockfish = require("stockfish")(enginePath || "lite-single", onReady);

stockfish.listener = function (line)
{
    console.log("STDOUT:", line);
    if (/bestmove \S+/.test(line)) {
        console.log("The best move is " + line.match(/bestmove (\S+)/)[1] + ".");
        stockfish.terminate();
    }
};

///NOTE: The first argument will be NULL if there are no errors.
///      The second argument is the exact same object as the stockfish object above. This is just a different way to obtain it.
function onReady(err/*, stockfish*/)
{
    if (err) {
        throw new Error(err);
    }
    stockfish.sendCommand("uci");
    stockfish.sendCommand("go depth 5");
}
