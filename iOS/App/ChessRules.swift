import Foundation

enum ChessRules {
    struct Outcome: Equatable {
        let title: String
        let detail: String
    }

    static func legalMoves(from square: String, in fen: String) -> [String] {
        let position = ParsedPosition.parse(fen: fen)
        guard let (file, rank) = decode(square), let piece = at(file, rank, position.pieces), piece.side == position.sideToMove else { return [] }
        return pseudoMoves(for: piece, in: position).filter { move in
            let next = apply(move, to: fen)
            return !isKingAttacked(piece.side, in: ParsedPosition.parse(fen: next))
        }
    }

    static func allLegalMoves(in fen: String) -> [String] {
        let position = ParsedPosition.parse(fen: fen)
        return position.pieces.filter { $0.side == position.sideToMove }.flatMap { legalMoves(from: $0.uciSquare, in: fen) }
    }

    static func outcome(in fen: String, history: [String]) -> Outcome? {
        let position = ParsedPosition.parse(fen: fen)
        if allLegalMoves(in: fen).isEmpty {
            if isKingAttacked(position.sideToMove, in: position) {
                let winner = position.sideToMove == .white ? "黑方" : "白方"
                return Outcome(title: "\(winner)获胜", detail: "将死。\(winner)赢得本局。")
            }
            return Outcome(title: "和棋", detail: "逼和：行棋方没有合法着法，但王未被将军。")
        }
        if position.halfmove >= 100 {
            return Outcome(title: "和棋", detail: "五十回合内没有吃子或兵的移动。")
        }
        let key = fen.split(separator: " ").prefix(4).joined(separator: " ")
        let repetitions = history.filter { $0.split(separator: " ").prefix(4).joined(separator: " ") == key }.count
        if repetitions >= 3 {
            return Outcome(title: "和棋", detail: "同一局面已第三次出现。")
        }
        if hasInsufficientMaterial(position.pieces) {
            return Outcome(title: "和棋", detail: "双方子力不足以将死对方。")
        }
        return nil
    }

    static func apply(_ move: String, to fen: String) -> String {
        guard move.count >= 4 else { return fen }
        let position = ParsedPosition.parse(fen: fen)
        let chars = Array(move)
        guard let from = decode(String(chars[0...1])), let to = decode(String(chars[2...3])),
              let moving = at(from.0, from.1, position.pieces) else { return fen }
        var pieces = position.pieces.filter { !($0.file == from.0 && $0.rank == from.1) && !($0.file == to.0 && $0.rank == to.1) }
        if moving.kind == .pawn, position.enPassant == String(chars[2...3]), from.0 != to.0, at(to.0, to.1, position.pieces) == nil {
            pieces.removeAll { $0.file == to.0 && $0.rank == from.1 && $0.kind == .pawn }
        }
        if moving.kind == .king, abs(to.0 - from.0) == 2 {
            let rookFrom = to.0 == 6 ? 7 : 0, rookTo = to.0 == 6 ? 5 : 3
            pieces.removeAll { $0.file == rookFrom && $0.rank == from.1 }
            pieces.append(BoardPiece(side: moving.side, kind: .rook, file: rookTo, rank: from.1))
        }
        let promotion: PieceKind = {
            guard chars.count > 4 else { return moving.kind }
            switch chars[4] { case "r": return .rook; case "b": return .bishop; case "n": return .knight; default: return .queen }
        }()
        pieces.append(BoardPiece(side: moving.side, kind: promotion, file: to.0, rank: to.1))

        var castling = position.castling
        if moving.kind == .king { castling.removeAll { moving.side == .white ? "KQ".contains($0) : "kq".contains($0) } }
        if moving.kind == .rook || at(to.0, to.1, position.pieces)?.kind == .rook {
            let rights: [((Int, Int), Character)] = [((0,7),"Q"),((7,7),"K"),((0,0),"q"),((7,0),"k")]
            for (square, right) in rights where square == from || square == to { castling.removeAll { $0 == right } }
        }
        if castling.isEmpty { castling = "-" }
        let ep: String
        if moving.kind == .pawn && abs(to.1 - from.1) == 2 { ep = encode(from.0, (from.1 + to.1) / 2) } else { ep = "-" }
        let capture = at(to.0, to.1, position.pieces) != nil || moving.kind == .pawn && from.0 != to.0
        let halfmove = moving.kind == .pawn || capture ? 0 : position.halfmove + 1
        let fullmove = position.fullmove + (moving.side == .black ? 1 : 0)
        let next = ParsedPosition(pieces: pieces, sideToMove: moving.side.opposite, castling: castling, enPassant: ep, halfmove: halfmove, fullmove: fullmove)
        return next.fen()
    }

    private static func pseudoMoves(for piece: BoardPiece, in position: ParsedPosition) -> [String] {
        var targets: [(Int, Int)] = []
        func add(_ f: Int, _ r: Int) {
            guard (0..<8).contains(f), (0..<8).contains(r), at(f, r, position.pieces)?.side != piece.side else { return }
            targets.append((f, r))
        }
        func slide(_ df: Int, _ dr: Int) {
            var f = piece.file + df, r = piece.rank + dr
            while (0..<8).contains(f), (0..<8).contains(r) {
                if let target = at(f, r, position.pieces) { if target.side != piece.side { targets.append((f,r)) }; break }
                targets.append((f,r)); f += df; r += dr
            }
        }
        switch piece.kind {
        case .pawn:
            let direction = piece.side == .white ? -1 : 1, start = piece.side == .white ? 6 : 1
            if at(piece.file, piece.rank + direction, position.pieces) == nil {
                add(piece.file, piece.rank + direction)
                if piece.rank == start, at(piece.file, piece.rank + direction * 2, position.pieces) == nil { add(piece.file, piece.rank + direction * 2) }
            }
            for df in [-1, 1] {
                let f = piece.file + df, r = piece.rank + direction
                if at(f, r, position.pieces)?.side == piece.side.opposite || position.enPassant == encode(f, r) { add(f, r) }
            }
        case .knight: for (df,dr) in [(1,2),(2,1),(2,-1),(1,-2),(-1,-2),(-2,-1),(-2,1),(-1,2)] { add(piece.file+df,piece.rank+dr) }
        case .bishop: for d in [(1,1),(1,-1),(-1,1),(-1,-1)] { slide(d.0,d.1) }
        case .rook: for d in [(1,0),(-1,0),(0,1),(0,-1)] { slide(d.0,d.1) }
        case .queen: for d in [(1,0),(-1,0),(0,1),(0,-1),(1,1),(1,-1),(-1,1),(-1,-1)] { slide(d.0,d.1) }
        case .king:
            for df in -1...1 { for dr in -1...1 where df != 0 || dr != 0 { add(piece.file+df,piece.rank+dr) } }
            let rank = piece.side == .white ? 7 : 0
            if piece.rank == rank && piece.file == 4 && !isKingAttacked(piece.side, in: position) {
                let kingRight: Character = piece.side == .white ? "K" : "k", queenRight: Character = piece.side == .white ? "Q" : "q"
                if position.castling.contains(kingRight), at(5,rank,position.pieces) == nil, at(6,rank,position.pieces) == nil,
                   !attacked(5,rank,by:piece.side.opposite,in:position), !attacked(6,rank,by:piece.side.opposite,in:position) { add(6,rank) }
                if position.castling.contains(queenRight), at(1,rank,position.pieces) == nil, at(2,rank,position.pieces) == nil, at(3,rank,position.pieces) == nil,
                   !attacked(3,rank,by:piece.side.opposite,in:position), !attacked(2,rank,by:piece.side.opposite,in:position) { add(2,rank) }
            }
        }
        return targets.flatMap { target -> [String] in
            let base = piece.uciSquare + encode(target.0,target.1)
            if piece.kind == .pawn && (target.1 == 0 || target.1 == 7) { return ["q","r","b","n"].map { base + $0 } }
            return [base]
        }
    }

    private static func isKingAttacked(_ side: ChessSide, in position: ParsedPosition) -> Bool {
        guard let king = position.pieces.first(where: { $0.side == side && $0.kind == .king }) else { return true }
        return attacked(king.file, king.rank, by: side.opposite, in: position)
    }

    private static func hasInsufficientMaterial(_ pieces: [BoardPiece]) -> Bool {
        let nonKings = pieces.filter { $0.kind != .king }
        if nonKings.isEmpty { return true }
        if nonKings.count == 1, let kind = nonKings.first?.kind, kind == .bishop || kind == .knight { return true }
        if nonKings.allSatisfy({ $0.kind == .bishop }) {
            let squareColors = Set(nonKings.map { ($0.file + $0.rank) % 2 })
            return squareColors.count == 1
        }
        return false
    }

    private static func attacked(_ file: Int, _ rank: Int, by side: ChessSide, in position: ParsedPosition) -> Bool {
        for piece in position.pieces where piece.side == side {
            let dx = file-piece.file, dy = rank-piece.rank, ax = abs(dx), ay = abs(dy)
            switch piece.kind {
            case .pawn: if dy == (side == .white ? -1 : 1) && ax == 1 { return true }
            case .knight: if ax * ay == 2 { return true }
            case .king: if max(ax,ay) == 1 { return true }
            case .bishop where ax == ay: if clear(piece.file,piece.rank,file,rank,position.pieces) { return true }
            case .rook where dx == 0 || dy == 0: if clear(piece.file,piece.rank,file,rank,position.pieces) { return true }
            case .queen where dx == 0 || dy == 0 || ax == ay: if clear(piece.file,piece.rank,file,rank,position.pieces) { return true }
            default: break
            }
        }
        return false
    }

    private static func clear(_ f1:Int,_ r1:Int,_ f2:Int,_ r2:Int,_ pieces:[BoardPiece]) -> Bool {
        let df = (f2-f1).signum(), dr = (r2-r1).signum(); var f=f1+df, r=r1+dr
        while f != f2 || r != r2 { if at(f,r,pieces) != nil { return false }; f += df; r += dr }; return true
    }
    private static func at(_ f:Int,_ r:Int,_ pieces:[BoardPiece]) -> BoardPiece? { pieces.first { $0.file == f && $0.rank == r } }
    private static func decode(_ square:String) -> (Int,Int)? { let c=Array(square); guard c.count==2, let a=c[0].asciiValue, let n=c[1].wholeNumberValue else{return nil}; return (Int(a-97),8-n) }
    private static func encode(_ f:Int,_ r:Int) -> String { guard (0..<8).contains(f),(0..<8).contains(r) else{return "-"}; return "\(Character(UnicodeScalar(97+f)!))\(8-r)" }
}
