import Foundation

enum ChessSide: String, Sendable {
    case white, black
    var opposite: ChessSide { self == .white ? .black : .white }
    var title: String { self == .white ? "白方" : "黑方" }
}

enum GameMode: String, CaseIterable, Identifiable, Sendable {
    case local, computer, setup
    var id: String { rawValue }
    var title: String {
        switch self { case .local: "双人对弈"; case .computer: "人机对战"; case .setup: "摆盘" }
    }
}

enum SetupTool: Hashable, Sendable { case move, erase, piece(ChessSide, PieceKind) }
enum PieceKind: String, CaseIterable, Hashable, Sendable { case king, queen, rook, bishop, knight, pawn }

struct BoardPiece: Identifiable, Hashable, Sendable {
    let side: ChessSide
    let kind: PieceKind
    let file: Int
    let rank: Int
    var id: String { "\(side.rawValue)-\(kind.rawValue)-\(file)-\(rank)" }
    var symbol: String {
        switch (side, kind) {
        case (.white, .king): "♔"; case (.white, .queen): "♕"; case (.white, .rook): "♖"
        case (.white, .bishop): "♗"; case (.white, .knight): "♘"; case (.white, .pawn): "♙"
        case (.black, .king): "♚"; case (.black, .queen): "♛"; case (.black, .rook): "♜"
        case (.black, .bishop): "♝"; case (.black, .knight): "♞"; case (.black, .pawn): "♟"
        }
    }
    var uciSquare: String { "\(Character(UnicodeScalar(97 + file)!))\(8 - rank)" }
}

struct ParsedPosition: Sendable {
    let pieces: [BoardPiece]
    let sideToMove: ChessSide
    let castling: String
    let enPassant: String
    let halfmove: Int
    let fullmove: Int

    static func parse(fen: String) -> ParsedPosition {
        let fields = fen.split(separator: " ").map(String.init)
        let rows = fields.first?.split(separator: "/") ?? []
        var pieces: [BoardPiece] = []
        for (rank, row) in rows.prefix(8).enumerated() {
            var file = 0
            for character in row {
                if let count = character.wholeNumberValue { file += count; continue }
                guard file < 8, let kind = pieceKind(for: character) else { continue }
                pieces.append(BoardPiece(side: character.isUppercase ? .white : .black, kind: kind, file: file, rank: rank))
                file += 1
            }
        }
        return ParsedPosition(pieces: pieces,
                              sideToMove: fields.count > 1 && fields[1] == "b" ? .black : .white,
                              castling: fields.count > 2 ? fields[2] : "-",
                              enPassant: fields.count > 3 ? fields[3] : "-",
                              halfmove: fields.count > 4 ? Int(fields[4]) ?? 0 : 0,
                              fullmove: fields.count > 5 ? Int(fields[5]) ?? 1 : 1)
    }

    func fen(side: ChessSide? = nil) -> String {
        var rows: [String] = []
        for rank in 0..<8 {
            var row = "", empty = 0
            for file in 0..<8 {
                if let piece = pieces.first(where: { $0.file == file && $0.rank == rank }) {
                    if empty > 0 { row += String(empty); empty = 0 }
                    var letter: Character
                    switch piece.kind { case .king: letter = "k"; case .queen: letter = "q"; case .rook: letter = "r"; case .bishop: letter = "b"; case .knight: letter = "n"; case .pawn: letter = "p" }
                    row.append(piece.side == .white ? Character(String(letter).uppercased()) : letter)
                } else { empty += 1 }
            }
            if empty > 0 { row += String(empty) }
            rows.append(row)
        }
        let active = (side ?? sideToMove) == .white ? "w" : "b"
        return "\(rows.joined(separator: "/")) \(active) \(castling) \(enPassant) \(halfmove) \(fullmove)"
    }

    private static func pieceKind(for character: Character) -> PieceKind? {
        switch character.lowercased() { case "k": .king; case "q": .queen; case "r": .rook; case "b": .bishop; case "n": .knight; case "p": .pawn; default: nil }
    }
}

struct AnalysisCandidate: Identifiable, Equatable, Sendable {
    let id: String
    let rank: Int
    let move: String
    let displayMove: String
    let scoreText: String
    let scorePawns: Double
    let depth: Int
    let pv: [String]
}

struct EvaluationPoint: Identifiable, Sendable { let id = UUID(); let ply: Int; let value: Double }
struct SavedGame: Identifiable, Codable, Sendable { let id: UUID; let title: String; let savedAt: Date; let initialFEN: String; let moves: [String] }

enum ChessDefaults {
    static let startFEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
}

struct EnginePayload: Decodable, Sendable { let lines: [EngineLine]; let error: String? }
struct EngineLine: Decodable, Sendable {
    let depth: Int
    let selDepth: Int
    let multipv: Int
    let score: String
    let pv: String
    let nodes: Int
    let nps: Int
    var moves: [String] { pv.split(separator: " ").map(String.init) }
    var centipawns: Int? {
        let fields = score.split(separator: " ")
        if let i = fields.firstIndex(of: "cp"), fields.indices.contains(i + 1) { return Int(fields[i + 1]) }
        if let i = fields.firstIndex(of: "mate"), fields.indices.contains(i + 1), let mate = Int(fields[i + 1]) { return mate > 0 ? 100_000 : -100_000 }
        return nil
    }
}
