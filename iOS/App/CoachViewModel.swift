import Foundation

@MainActor
final class CoachViewModel: ObservableObject {
    @Published private(set) var fen = ChessDefaults.startFEN
    @Published private(set) var pieces: [BoardPiece] = ParsedPosition.parse(fen: ChessDefaults.startFEN).pieces
    @Published private(set) var sideToMove: ChessSide = .white
    @Published var gameMode: GameMode = .local
    @Published var humanSide: ChessSide = .white
    @Published var boardFlipped = false
    @Published var depth = 14
    @Published var multiPV = 5
    @Published var computerElo = 2100
    @Published private(set) var candidates: [AnalysisCandidate] = []
    @Published private(set) var selectedCandidates: [AnalysisCandidate] = []
    @Published private(set) var isAnalyzing = false
    @Published private(set) var isAnalyzingSelection = false
    @Published private(set) var engineStatus = "Stockfish 准备中"
    @Published private(set) var selectedSquare: String?
    @Published private(set) var legalTargets: Set<String> = []
    @Published private(set) var lastMove: String?
    @Published private(set) var moveHistory: [String] = []
    @Published private(set) var fenHistory: [String] = [ChessDefaults.startFEN]
    @Published private(set) var redoMoves: [String] = []
    @Published private(set) var redoFens: [String] = []
    @Published private(set) var evaluations: [EvaluationPoint] = []
    @Published var showCandidateArrows = false
    @Published private(set) var savedGames: [SavedGame] = []
    @Published var setupTool: SetupTool = .move
    @Published private(set) var message: String?
    @Published private(set) var gameOutcome: ChessRules.Outcome?
    @Published var isShowingGameOutcome = false
    @Published private(set) var lastMoveGrade = "—"
    @Published private(set) var lastMoveReview = "等待落子，Stockfish 将评价着法质量。"

    private var analysisTask: Task<Void, Never>?
    private var generation = 0
    private var pendingBestScore: Double?

    var canHumanMove: Bool { gameOutcome == nil && (gameMode != .computer || sideToMove == humanSide) }
    var canUndo: Bool { !moveHistory.isEmpty }
    var canRedo: Bool { !redoMoves.isEmpty }

    func start() { loadSavedGames();scheduleAnalysis() }
    func setMode(_ mode: GameMode) {
        gameMode = mode
        selectedSquare=nil; legalTargets=[]
        if mode == .setup {
            cancelAnalysis(); gameOutcome=nil; isShowingGameOutcome=false; candidates=[]; engineStatus="摆盘模式 · 已暂停分析"
            return
        }
        refresh()
        if gameOutcome != nil { return }
        if mode == .computer && sideToMove != humanSide { playComputerMove() }
        else { scheduleAnalysis() }
    }

    func setHumanSide(_ side: ChessSide) {
        humanSide = side
        if gameMode == .computer && sideToMove != humanSide { playComputerMove() }
        else { scheduleAnalysis() }
    }

    func setComputerElo(_ elo: Int) {
        computerElo = min(3190, max(1320, elo))
        if gameMode == .computer && sideToMove != humanSide { playComputerMove() }
    }

    func tap(file: Int, rank: Int) {
        if gameMode == .setup { editSetup(file: file, rank: rank); return }
        guard canHumanMove else { return }
        let square = square(file, rank)
        if let selectedSquare, legalTargets.contains(square), let move = legalMove(from: selectedSquare, to: square) {
            commit(move)
            return
        }
        if pieces.contains(where: { $0.file == file && $0.rank == rank && $0.side == sideToMove }) {
            selectedSquare = square
            let moves=ChessRules.legalMoves(from:square,in:fen)
            legalTargets = Set(moves.map { String($0.dropFirst(2).prefix(2)) })
            analyzeSelection(moves)
        } else { selectedSquare = nil; legalTargets = []; selectedCandidates=[]; scheduleAnalysis() }
    }

    func reset() { cancelAnalysis(); gameOutcome=nil;isShowingGameOutcome=false;fen = ChessDefaults.startFEN; moveHistory=[]; fenHistory=[fen]; redoMoves=[]; redoFens=[]; evaluations=[]; lastMove=nil; lastMoveGrade="—";lastMoveReview="等待落子，Stockfish 将评价着法质量。";refresh(); scheduleAnalysis() }
    func undo() {
        guard let move = moveHistory.last, let currentFen = fenHistory.last else { return }
        cancelAnalysis(); redoMoves.insert(move, at: 0); redoFens.insert(currentFen, at: 0); moveHistory.removeLast(); fenHistory.removeLast(); fen = fenHistory.last ?? ChessDefaults.startFEN; lastMove = moveHistory.last; refresh(); scheduleAnalysis()
    }
    func redo() { guard !redoMoves.isEmpty, !redoFens.isEmpty else { return }; cancelAnalysis(); let move=redoMoves.removeFirst(), next=redoFens.removeFirst(); moveHistory.append(move);fenHistory.append(next);fen=next;lastMove=move;refresh();scheduleAnalysis() }
    func flip() { boardFlipped.toggle() }
    func clearSelection(){selectedSquare=nil;legalTargets=[];selectedCandidates=[];scheduleAnalysis()}
    func toggleCandidateArrows() { showCandidateArrows.toggle() }
    func playCandidate(_ move: String) { guard ChessRules.allLegalMoves(in: fen).contains(move) else { return }; commit(move) }
    func goToPly(_ ply: Int) {
        let safe = max(0, min(ply, moveHistory.count)); cancelAnalysis(); let removedMoves=Array(moveHistory.dropFirst(safe)),removedFens=Array(fenHistory.dropFirst(safe+1))
        fen = fenHistory[safe]; moveHistory = Array(moveHistory.prefix(safe)); fenHistory = Array(fenHistory.prefix(safe + 1)); redoMoves=removedMoves+redoMoves;redoFens=removedFens+redoFens;lastMove = moveHistory.last
        refresh(); scheduleAnalysis()
    }
    func importFEN(_ value: String) {
        let text=value.trimmingCharacters(in:.whitespacesAndNewlines), parsed=ParsedPosition.parse(fen:text)
        guard parsed.pieces.filter({$0.kind == .king}).count == 2 else { message="FEN 必须包含双方的王。"; return }
        cancelAnalysis(); fen=text; moveHistory=[]; fenHistory=[text]; redoMoves=[]; redoFens=[]; evaluations=[]; lastMove=nil; refresh(); scheduleAnalysis()
    }
    func saveGame() {
        let game=SavedGame(id:UUID(),title:"国际象棋对局 · \(Date().formatted(date:.numeric,time:.shortened))",savedAt:Date(),initialFEN:fenHistory.first ?? ChessDefaults.startFEN,moves:moveHistory)
        savedGames.insert(game,at:0); persistSavedGames(); message="棋局已保存到本机。"
    }
    func loadSavedGame(_ game:SavedGame) {
        cancelAnalysis(); var current=game.initialFEN; var positions=[current]; var valid:[String]=[]
        for move in game.moves where ChessRules.allLegalMoves(in: current).contains(move) { current=ChessRules.apply(move,to:current);positions.append(current);valid.append(move) }
        fen=current;moveHistory=valid;fenHistory=positions;redoMoves=[];redoFens=[];lastMove=valid.last;refresh();scheduleAnalysis()
    }
    func dismissMessage(){message=nil}
    func setSetupTool(_ tool:SetupTool){setupTool=tool}
    func finishSetup(){setMode(.local)}

    func scheduleAnalysis() {
        guard gameMode != .setup, gameOutcome == nil else { return }
        cancelAnalysis()
        let requestedFen = fen, requestedGeneration = generation, requestedDepth = depth, requestedMultiPV = multiPV
        isAnalyzing = true; engineStatus = "Stockfish 计算中"
        analysisTask = Task { [weak self] in
            do {
                let previewDepth = min(8, requestedDepth)
                var lines = try await StockfishService.shared.analyze(
                    fen: requestedFen,
                    depth: previewDepth,
                    multiPV: requestedMultiPV
                )
                guard let self, !Task.isCancelled, requestedGeneration == self.generation, requestedFen == self.fen else { return }
                self.publishCandidates(lines, generation: requestedGeneration)

                if previewDepth < requestedDepth {
                    self.engineStatus = "Stockfish · 深度 \(lines.first?.depth ?? previewDepth) · 继续分析"
                    lines = try await StockfishService.shared.analyze(
                        fen: requestedFen,
                        depth: requestedDepth,
                        multiPV: requestedMultiPV
                    )
                    guard !Task.isCancelled, requestedGeneration == self.generation, requestedFen == self.fen else { return }
                    self.publishCandidates(lines, generation: requestedGeneration)
                }
                if let cp = lines.first?.centipawns {
                    let white = self.sideToMove == .white ? cp : -cp
                    self.evaluations.append(EvaluationPoint(ply: self.moveHistory.count, value: Double(white)/100))
                    if let before=self.pendingBestScore {
                        let moverAfter = -Double(cp)/100
                        self.updateReview(loss:max(0,before-moverAfter))
                        self.pendingBestScore=nil
                    }
                }
                self.isAnalyzing = false; self.engineStatus = "Stockfish · 深度 \(lines.first?.depth ?? requestedDepth)"
            } catch is CancellationError { } catch {
                guard let self, requestedGeneration == self.generation else { return }
                self.isAnalyzing=false; self.engineStatus=error.localizedDescription
            }
        }
    }

    private func commit(_ move: String) {
        pendingBestScore=candidates.first?.scorePawns
        cancelAnalysis() // rules and UI commit happen synchronously before any new search
        fen = ChessRules.apply(move, to: fen)
        moveHistory.append(move); fenHistory.append(fen); redoMoves=[];redoFens=[];lastMove=move; refresh()
        if gameOutcome != nil { return }
        if gameMode == .computer && sideToMove != humanSide { playComputerMove() } else { scheduleAnalysis() }
    }

    private func playComputerMove() {
        cancelAnalysis(); let requestedFen=fen, requestedGeneration=generation
        isAnalyzing=true; engineStatus="Stockfish 正在思考"
        analysisTask = Task { [weak self] in
            do {
                guard let self else { return }
                let move = try await StockfishService.shared.bestMove(
                    fen: requestedFen, depth: self.depth, elo: self.computerElo
                )
                guard !Task.isCancelled, requestedGeneration == self.generation, requestedFen == self.fen,
                      ChessRules.allLegalMoves(in: requestedFen).contains(move) else { return }
                self.commit(move)
            } catch { self?.isAnalyzing=false; self?.engineStatus=error.localizedDescription }
        }
    }

    private func cancelAnalysis() { generation += 1; analysisTask?.cancel(); analysisTask=nil; StockfishService.shared.interruptForBoardAction(); isAnalyzing=false }
    private func refresh() {
        let p=ParsedPosition.parse(fen: fen); pieces=p.pieces; sideToMove=p.sideToMove; selectedSquare=nil; legalTargets=[];selectedCandidates=[];isAnalyzingSelection=false
        let nextOutcome = gameMode == .setup ? nil : ChessRules.outcome(in: fen, history: fenHistory)
        if nextOutcome != gameOutcome { gameOutcome = nextOutcome; isShowingGameOutcome = nextOutcome != nil }
        if let nextOutcome { engineStatus = nextOutcome.title; candidates = []; isAnalyzing = false }
    }
    private func square(_ file:Int,_ rank:Int)->String { "\(Character(UnicodeScalar(97+file)!))\(8-rank)" }
    private func legalMove(from:String,to:String)->String? { ChessRules.legalMoves(from: from, in: fen).first { String($0.dropFirst(2).prefix(2)) == to } }
    private func describe(_ move:String)->String { move.count > 4 ? "\(move.prefix(4))=\(move.suffix(1).uppercased())" : move }
    private func whiteScore(_ line:EngineLine)->Int { let cp=line.centipawns ?? 0; return sideToMove == .white ? cp : -cp }
    private func scoreText(_ line:EngineLine)->String { let cp=whiteScore(line); if abs(cp) >= 100_000 { return cp >= 0 ? "白方将杀" : "黑方将杀" }; return String(format: "%+.2f", Double(cp)/100) }
    private func publishCandidates(_ lines: [EngineLine], generation: Int) {
        candidates = lines.enumerated().compactMap { index, line in
            guard let move = line.moves.first else { return nil }
            let cp = line.centipawns ?? 0
            return AnalysisCandidate(
                id: "\(generation)-\(index)-\(move)", rank: index + 1, move: move,
                displayMove: describe(move), scoreText: scoreText(line),
                scorePawns: Double(cp) / 100, depth: line.depth, pv: line.moves
            )
        }
    }
    private func analyzeSelection(_ moves:[String]) {
        guard !moves.isEmpty,gameMode != .setup else { selectedCandidates=[];return }
        cancelAnalysis();let requestedFen=fen,requestedGeneration=generation,requestedSquare=selectedSquare
        isAnalyzingSelection=true;engineStatus="Stockfish · 正在分析选中棋子"
        analysisTask=Task { [weak self] in
            do {
                let requestedDepth = self?.depth ?? 14
                let requestedMultiPV = min(moves.count, self?.multiPV ?? 5)
                let previewDepth = min(8, requestedDepth)
                var lines=try await StockfishService.shared.analyze(fen:requestedFen,depth:previewDepth,multiPV:requestedMultiPV,searchMoves:moves)
                guard let self,!Task.isCancelled,requestedGeneration==self.generation,requestedFen==self.fen,requestedSquare==self.selectedSquare else{return}
                self.publishSelectedCandidates(lines, generation: requestedGeneration)
                if previewDepth < requestedDepth {
                    self.engineStatus="Stockfish · 已显示快速候选 · 继续分析"
                    lines=try await StockfishService.shared.analyze(fen:requestedFen,depth:requestedDepth,multiPV:requestedMultiPV,searchMoves:moves)
                    guard !Task.isCancelled,requestedGeneration==self.generation,requestedFen==self.fen,requestedSquare==self.selectedSquare else{return}
                    self.publishSelectedCandidates(lines, generation: requestedGeneration)
                }
                self.isAnalyzingSelection=false;self.engineStatus="Stockfish · 已分析这枚棋子"
            } catch is CancellationError {} catch {
                guard let self,requestedGeneration==self.generation else{return};self.isAnalyzingSelection=false;self.engineStatus=error.localizedDescription
            }
        }
    }
    private func publishSelectedCandidates(_ lines: [EngineLine], generation: Int) {
        selectedCandidates=lines.enumerated().compactMap { index,line in
            guard let move=line.moves.first else{return nil}
            return AnalysisCandidate(id:"selected-\(generation)-\(index)-\(move)",rank:index+1,move:move,displayMove:describe(move),scoreText:scoreText(line),scorePawns:Double(line.centipawns ?? 0)/100,depth:line.depth,pv:line.moves)
        }
    }
    private func updateReview(loss:Double) {
        switch loss {
        case ..<0.15: lastMoveGrade="最佳";lastMoveReview="这步基本保持了 Stockfish 的最优评估。"
        case ..<0.45: lastMoveGrade="优秀";lastMoveReview="这步很稳健，与首选只有很小差距。"
        case ..<1.0: lastMoveGrade="可行";lastMoveReview="这步可下，但候选首选能保留更多优势。"
        case ..<2.5: lastMoveGrade="失误";lastMoveReview="这步导致明显掉分，建议回看落子前的候选着法。"
        default: lastMoveGrade="严重失误";lastMoveReview="局面评估大幅下降，请重点检查将杀、捉子和未受保护的棋子。"
        }
    }
    private func editSetup(file:Int,rank:Int) {
        cancelAnalysis();var p=ParsedPosition.parse(fen:fen),pieces=p.pieces
        switch setupTool {
        case .erase: pieces.removeAll{$0.file==file && $0.rank==rank}
        case .piece(let side,let kind): pieces.removeAll{$0.file==file && $0.rank==rank};pieces.append(BoardPiece(side:side,kind:kind,file:file,rank:rank))
        case .move:
            let sq=square(file,rank)
            if let selectedSquare,let moving=pieces.first(where:{$0.uciSquare==selectedSquare}) { pieces.removeAll{$0.uciSquare==selectedSquare || ($0.file==file&&$0.rank==rank)};pieces.append(BoardPiece(side:moving.side,kind:moving.kind,file:file,rank:rank));self.selectedSquare=nil }
            else if pieces.contains(where:{$0.uciSquare==sq}) { selectedSquare=sq; return }
        }
        p=ParsedPosition(pieces:pieces,sideToMove:p.sideToMove,castling:"-",enPassant:"-",halfmove:0,fullmove:1);fen=p.fen();fenHistory=[fen];moveHistory=[];refresh()
    }
    private func loadSavedGames(){guard let data=UserDefaults.standard.data(forKey:"yisi.chess.saved"),let games=try? JSONDecoder().decode([SavedGame].self,from:data)else{return};savedGames=games}
    private func persistSavedGames(){if let data=try? JSONEncoder().encode(savedGames){UserDefaults.standard.set(data,forKey:"yisi.chess.saved")}}
}
