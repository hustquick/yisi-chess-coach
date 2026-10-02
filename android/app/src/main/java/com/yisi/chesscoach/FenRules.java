package com.yisi.chesscoach;

/** Reject malformed/unsafe positions before handing them to the native engine. */
final class FenRules {
    static String validate(String input) {
        String fen = input.trim().replaceAll("\\s+", " ");
        String[] fields = fen.split(" ");
        if (fields.length != 6) throw new IllegalArgumentException("FEN 必须包含六个字段。");
        String[] ranks = fields[0].split("/", -1);
        if (ranks.length != 8) throw new IllegalArgumentException("棋盘必须有八行。");
        char[][] board = new char[8][8];
        int whiteKing = -1, blackKing = -1, whites = 0, blacks = 0, whitePawns = 0, blackPawns = 0;
        for (int r = 0; r < 8; r++) {
            int f = 0;
            boolean number = false;
            for (char p : ranks[r].toCharArray()) {
                if (p >= '1' && p <= '8') {
                    if (number) throw new IllegalArgumentException("空格数量必须合并表示。");
                    f += p - '0'; number = true;
                } else {
                    if ("KQRBNPkqrbnp".indexOf(p) < 0 || f >= 8) throw new IllegalArgumentException("棋子或行长度不正确。");
                    board[r][f] = p;
                    if (Character.isUpperCase(p)) whites++; else blacks++;
                    if (p == 'K') { if (whiteKing >= 0) throw new IllegalArgumentException("白王只能有一个。"); whiteKing = r * 8 + f; }
                    if (p == 'k') { if (blackKing >= 0) throw new IllegalArgumentException("黑王只能有一个。"); blackKing = r * 8 + f; }
                    if (p == 'P') whitePawns++;
                    if (p == 'p') blackPawns++;
                    if (Character.toLowerCase(p) == 'p' && (r == 0 || r == 7)) throw new IllegalArgumentException("兵不能留在第一或第八横线。");
                    f++; number = false;
                }
                if (f > 8) throw new IllegalArgumentException("每行必须恰好八格。");
            }
            if (f != 8) throw new IllegalArgumentException("每行必须恰好八格。");
        }
        if (whiteKing < 0 || blackKing < 0) throw new IllegalArgumentException("必须各有一个白王和黑王。");
        if (whites > 16 || blacks > 16 || whitePawns > 8 || blackPawns > 8) throw new IllegalArgumentException("棋子数量超出正常对局限制。");
        if (!fields[1].matches("[wb]")) throw new IllegalArgumentException("行棋方必须是 w 或 b。");
        if (!fields[2].matches("-|K?Q?k?q?") || fields[2].isEmpty()) throw new IllegalArgumentException("王车易位字段不正确。");
        if (fields[2].contains("K") && (board[7][4] != 'K' || board[7][7] != 'R')
                || fields[2].contains("Q") && (board[7][4] != 'K' || board[7][0] != 'R')
                || fields[2].contains("k") && (board[0][4] != 'k' || board[0][7] != 'r')
                || fields[2].contains("q") && (board[0][4] != 'k' || board[0][0] != 'r'))
            throw new IllegalArgumentException("王车易位权与王、车的位置不符。");
        if (!fields[3].equals("-")) {
            if (!fields[3].matches("[a-h][36]")) throw new IllegalArgumentException("吃过路兵目标不正确。");
            int f = fields[3].charAt(0) - 'a', r = 8 - (fields[3].charAt(1) - '0');
            boolean white = fields[1].equals("w");
            if (r != (white ? 2 : 5) || board[r][f] != 0 || board[r + (white ? 1 : -1)][f] != (white ? 'p' : 'P')
                    || board[r + (white ? -1 : 1)][f] != 0) throw new IllegalArgumentException("吃过路兵目标与兵的位置不符。");
        }
        try {
            if (!fields[4].matches("\\d+") || !fields[5].matches("\\d+") || Integer.parseInt(fields[4]) > 100000
                    || Integer.parseInt(fields[5]) < 1 || Integer.parseInt(fields[5]) > 100000) throw new NumberFormatException();
        } catch (NumberFormatException e) { throw new IllegalArgumentException("回合计数不正确。"); }
        // The king of the side that just moved may not be left in check.
        boolean whiteToMove = fields[1].equals("w");
        if (attacked(board, whiteToMove ? blackKing : whiteKing, whiteToMove))
            throw new IllegalArgumentException("未行棋一方的王不能处于被将军状态。");
        return fen;
    }

    private static boolean attacked(char[][] board, int king, boolean byWhite) {
        int kr = king / 8, kf = king % 8;
        for (int r = 0; r < 8; r++) for (int f = 0; f < 8; f++) {
            char p = board[r][f];
            if (p == 0 || Character.isUpperCase(p) != byWhite) continue;
            int dr = kr - r, df = kf - f;
            char type = Character.toLowerCase(p);
            if (type == 'p' && dr == (byWhite ? -1 : 1) && Math.abs(df) == 1) return true;
            if (type == 'n' && Math.abs(dr) * Math.abs(df) == 2) return true;
            if (type == 'k' && Math.max(Math.abs(dr), Math.abs(df)) == 1) return true;
            boolean line = (type == 'r' || type == 'q') && (dr == 0 || df == 0)
                    || (type == 'b' || type == 'q') && Math.abs(dr) == Math.abs(df);
            if (!line) continue;
            int sr = Integer.signum(dr), sf = Integer.signum(df), rr = r + sr, ff = f + sf;
            while (rr != kr || ff != kf) { if (board[rr][ff] != 0) break; rr += sr; ff += sf; }
            if (rr == kr && ff == kf) return true;
        }
        return false;
    }
}
