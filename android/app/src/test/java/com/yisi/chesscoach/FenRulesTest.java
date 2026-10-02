package com.yisi.chesscoach;
import org.junit.Test;
import static org.junit.Assert.*;

public class FenRulesTest {
    @Test public void acceptsStartAndNormalEnPassant() {
        assertEquals(MainActivity.START,FenRules.validate(MainActivity.START));
        assertEquals(MainActivity.START,FenRules.validate("  "+MainActivity.START.replace(" ","  ")+"  "));
        FenRules.validate("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1");
        FenRules.validate("7k/P7/8/8/8/8/8/7K w - - 0 1");
        FenRules.validate("7k/6Q1/5K2/8/8/8/8/8 b - - 0 1");
    }
    @Test public void rejectsUnsafePositionsBeforeNativeCode() {
        String[] bad={
            "8/8/8/8/8/8/8/8 w - - 0 1",
            "7k/8/8/8/8/8/8/6KK w - - 0 1",
            "7k/8/8/8/8/8/8/7P w - - 0 1",
            "7k/8/8/8/8/8/8/7K x - - 0 1",
            "7k/8/8/8/8/8/8/7K w K - 0 1",
            "7k/8/8/8/8/8/8/7K w - e3 0 1",
            "7k/8/8/8/8/8/8/7K w - e6 0 1",
            "7k/8/8/8/8/8/8/7K w - - -1 1",
            "7k/8/8/8/8/8/8/7K w - - 0 0",
            "7k/8/8/8/8/8/8/7K w - - 0 999999999999",
            "7k/8/8/8/8/8/8/7K w - - 0",
            "7k/8/8/8/8/8/8/6K2 w - - 0 1",
            "7k/8/8/8/8/8/8/33K1 w - - 0 1",
            "7k/6Q1/5K2/8/8/8/8/8 w - - 0 1",
            "7k/7K/8/8/8/8/8/8 w - - 0 1",
            "7k/8/8/8/8/8/8/6XK w - - 0 1"
        };
        for(String fen:bad){try{FenRules.validate(fen);fail("Accepted invalid FEN: "+fen);}catch(IllegalArgumentException expected){}}
    }
}
