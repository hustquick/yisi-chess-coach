import SwiftUI

struct ContentView: View {
    @Environment(\.colorScheme) private var colorScheme
    @StateObject private var model = CoachViewModel()
    @State private var analysisExpanded = true
    @State private var chartExpanded = false
    @State private var recordExpanded = false
    @State private var settingsExpanded = false
    @State private var showsRecordSheet = false
    @State private var fenText = ""
    private var isDark: Bool { colorScheme == .dark }
    private var paper: Color {
        isDark ? Color(red:0.045,green:0.060,blue:0.052) : Color(red:0.97,green:0.96,blue:0.92)
    }
    private var green: Color {
        isDark ? Color(red:0.34,green:0.77,blue:0.56) : Color(red:0.10,green:0.34,blue:0.24)
    }
    private var panel: Color {
        isDark ? Color(red:0.085,green:0.115,blue:0.100) : Color.white.opacity(0.72)
    }
    private var card: Color {
        isDark ? Color(red:0.105,green:0.145,blue:0.125) : Color.white.opacity(0.66)
    }
    private var softCard: Color {
        isDark ? Color(red:0.090,green:0.125,blue:0.108) : Color.white.opacity(0.62)
    }
    private var chartSurface: Color {
        isDark ? Color(red:0.070,green:0.095,blue:0.082) : Color.white.opacity(0.65)
    }
    private var accentWash: Color { green.opacity(isDark ? 0.16 : 0.08) }
    private var panelBorder: Color { isDark ? Color.white.opacity(0.08) : Color.black.opacity(0.04) }

    var body: some View {
        GeometryReader { geo in
            Group {
                if geo.size.width >= 820 && geo.size.width > geo.size.height { landscape(size:geo.size) }
                else { portrait(size:geo.size) }
            }.background(paper.ignoresSafeArea())
        }
        .tint(green)
        .task { model.start() }
        .sheet(isPresented: $showsRecordSheet) { recordSheet }
        .alert("棋谱", isPresented: Binding(get: { model.message != nil }, set: { if !$0 { model.dismissMessage() } })) {
            Button("好") { model.dismissMessage() }
        } message: {
            Text(model.message ?? "")
        }
        .alert(model.gameOutcome?.title ?? "对局结束", isPresented: $model.isShowingGameOutcome) {
            Button("再来一局") { model.reset() }
            Button("查看棋局", role: .cancel) {}
        } message: {
            Text(model.gameOutcome?.detail ?? "")
        }
    }

    private func portrait(size:CGSize)->some View {
        let boardSize = min(size.width-28,size.height*0.66,760)
        return ScrollView {
            VStack(spacing:12) {
                header
                controls
                board.frame(width:boardSize,height:boardSize)
                modules
            }.padding(.horizontal,size.width >= 700 ? 24:14).padding(.vertical,10)
        }
    }
    private func landscape(size:CGSize)->some View {
        let boardSize = min(size.height-110,size.width*0.60)
        return VStack(spacing:10) {
            header.padding(.horizontal,24)
            HStack(alignment:.top,spacing:20) {
                VStack(spacing:10) { controls; board.frame(width:boardSize,height:boardSize) }.frame(maxWidth:.infinity)
                ScrollView { modules.padding(.trailing,5) }.frame(width:min(460,size.width*0.36))
            }.padding(.horizontal,24)
        }.padding(.top,8)
    }
    private var board:some View { ChessBoardView(viewModel:model) }
    private var header:some View {
        HStack {
            brandLogo
            VStack(alignment:.leading,spacing:1) {
                Text("弈思").font(.title2.bold())
                Text("国际象棋教练").font(.subheadline).foregroundStyle(.secondary)
            }
            Spacer(); Label(model.engineStatus,systemImage:model.isAnalyzing ? "circle.dotted":"checkmark.circle.fill")
                .font(.caption).foregroundStyle(model.isAnalyzing ? .orange:green)
                .lineLimit(1).minimumScaleFactor(0.75)
        }
    }
    @ViewBuilder private var brandLogo: some View {
        let resource=colorScheme == .dark ? "AppIconDark":"AppIcon"
        if let path=Bundle.main.path(forResource:resource,ofType:"png"),let image=UIImage(contentsOfFile:path) {
            Image(uiImage:image).resizable().scaledToFit().frame(width:58,height:58).clipShape(RoundedRectangle(cornerRadius:14))
        } else {
            Image(systemName:"arrow.up.forward.circle.fill").resizable().scaledToFit().frame(width:58,height:58).foregroundStyle(green)
        }
    }
    private var controls:some View {
        VStack(spacing:9) {
            Picker("模式",selection:Binding(get:{model.gameMode},set:{model.setMode($0)})) { ForEach(GameMode.allCases) { Text($0.title).tag($0) } }.pickerStyle(.segmented)
            HStack {
                Label("\(model.sideToMove.title)走棋",systemImage:"circle.fill")
                Spacer()
                Button { model.flip() } label:{Image(systemName:"arrow.up.arrow.down")}
                Button { model.toggleCandidateArrows() } label: {
                    Text("优").font(.system(size:17,weight:.bold,design:.serif))
                        .foregroundStyle(model.showCandidateArrows ? .white : green)
                        .frame(width:36,height:36).background(model.showCandidateArrows ? green : green.opacity(0.10),in:Circle())
                }.accessibilityLabel(model.showCandidateArrows ? "隐藏候选箭头" : "显示候选箭头")
                Button("悔棋",systemImage:"arrow.uturn.backward"){model.undo()}.disabled(model.moveHistory.isEmpty)
                Button("重开",systemImage:"arrow.clockwise"){model.reset()}
            }.buttonStyle(.borderless)
            if model.gameMode == .setup { setupControls }
        }
    }
    private var modules:some View {
        LazyVStack(spacing:12) {
            disclosure("教练分析",expanded:$analysisExpanded) { analysis }
            disclosure("局势图",expanded:$chartExpanded) { chart }
            disclosure("棋谱与存档",expanded:$recordExpanded) { records }
            disclosure("对弈与分析设置",expanded:$settingsExpanded) { settings }
            Text("Stockfish 为 GPL-3.0 开源引擎。本应用与 Arena、Cute Chess、Scid 一样通过 UCI 协议使用引擎。").font(.caption2).foregroundStyle(.secondary).padding(.vertical,8)
        }
    }
    private func disclosure<Content:View>(_ title:String,expanded:Binding<Bool>,@ViewBuilder content:()->Content)->some View {
        VStack(spacing:expanded.wrappedValue ? 10:0) {
            Button { withAnimation(.easeInOut(duration:0.2)){expanded.wrappedValue.toggle()} } label:{HStack{Text(title).font(.headline);Spacer();Image(systemName:expanded.wrappedValue ? "chevron.up.circle.fill":"chevron.down.circle.fill").foregroundStyle(green)}.padding(13).background(panel,in:RoundedRectangle(cornerRadius:12)).overlay(RoundedRectangle(cornerRadius:12).stroke(panelBorder,lineWidth:1))}.buttonStyle(.plain)
            if expanded.wrappedValue { content() }
        }
    }
    private var analysis:some View {
        let scoped=model.selectedSquare == nil ? model.candidates : model.selectedCandidates
        return VStack(alignment:.leading,spacing:8) {
            HStack {
                VStack(alignment:.leading,spacing:2) {
                    Text(model.moveHistory.isEmpty ? "开局建议" : "上一步：\(model.lastMoveGrade)").font(.headline)
                    Text(model.moveHistory.isEmpty ? "先看全局候选，再选择计划。" : model.lastMoveReview).font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
            }.padding(11).background(accentWash,in:RoundedRectangle(cornerRadius:10))
            HStack {
                Text(model.selectedSquare == nil ? "全局候选着法" : "\(model.selectedSquare!.uppercased()) 棋子的候选着法").font(.caption.bold()).foregroundStyle(.secondary)
                Spacer()
                if model.selectedSquare != nil { Button("返回全局") { model.clearSelection() }.font(.caption) }
            }
            if scoped.isEmpty { ProgressView(model.isAnalyzing || model.isAnalyzingSelection ? "Stockfish 正在计算…":"暂无候选着法").frame(maxWidth:.infinity,minHeight:80) }
            ForEach(scoped) { c in
                Button { model.playCandidate(c.move) } label: {
                    HStack {
                        Text("\(c.rank)").foregroundStyle(.secondary)
                        VStack(alignment:.leading,spacing:2) {
                            Text(c.displayMove).font(.system(.body,design:.monospaced).bold())
                            Text(c.pv.prefix(5).joined(separator:"  ")).font(.caption2.monospaced()).foregroundStyle(.secondary).lineLimit(1)
                        }
                        Spacer(); Text(c.rank == 1 ? "最佳" : "候选").font(.caption.bold()).foregroundStyle(c.rank == 1 ? green : .secondary)
                        Text(c.scoreText).foregroundStyle(green); Text("d\(c.depth)").font(.caption).foregroundStyle(.secondary)
                    }.padding(11).background(card,in:RoundedRectangle(cornerRadius:10)).overlay(RoundedRectangle(cornerRadius:10).stroke(panelBorder,lineWidth:1))
                }.buttonStyle(.plain).disabled(!model.canHumanMove || model.gameMode == .setup)
            }
        }
    }
    private var chart:some View {
        GeometryReader { g in
            let points=model.evaluations, mid=g.size.height/2
            ZStack { Rectangle().fill(chartSurface); Path{p in p.move(to:CGPoint(x:0,y:mid));p.addLine(to:CGPoint(x:g.size.width,y:mid))}.stroke(Color.secondary.opacity(0.4)); if points.count>1 { Path{p in for(i,pt) in points.enumerated(){let x=g.size.width*CGFloat(i)/CGFloat(points.count-1),y=mid-CGFloat(max(-8,min(8,pt.value)))/8*(mid-8);if i==0{p.move(to:CGPoint(x:x,y:y))}else{p.addLine(to:CGPoint(x:x,y:y))}}}.stroke(green,lineWidth:3) } }
        }.frame(height:150).clipShape(RoundedRectangle(cornerRadius:12))
    }
    private var records: some View {
        VStack(spacing:10) {
            HStack {
                VStack(alignment:.leading,spacing:2) {
                    Text(model.moveHistory.isEmpty ? "新对局" : "当前棋谱").font(.subheadline.bold())
                    Text("\(model.moveHistory.count) 半回合").font(.caption2).foregroundStyle(.secondary)
                }
                Spacer()
                Button("开局") { model.goToPly(0) }.disabled(model.moveHistory.isEmpty)
                Button("上一步") { model.undo() }.disabled(model.moveHistory.isEmpty)
                Button("载入") { fenText=model.fen; showsRecordSheet=true }
                Button("保存") { model.saveGame() }.buttonStyle(.borderedProminent).tint(green)
            }.buttonStyle(.bordered)
            if model.moveHistory.isEmpty {
                Text("还没有着法。可以载入 FEN 局面或本机存档。").font(.caption).foregroundStyle(.secondary).frame(maxWidth:.infinity,alignment:.leading)
            } else {
                ScrollView(.horizontal,showsIndicators:false) {
                    HStack(spacing:7) {
                        ForEach(Array(model.moveHistory.enumerated()),id:\.offset) { index,move in
                            Button("\(index / 2 + 1)\(index.isMultiple(of:2) ? "." : "…") \(move)") { model.goToPly(index+1) }
                                .font(.caption.monospaced()).buttonStyle(.bordered)
                        }
                    }
                }
            }
        }.padding(12).background(card,in:RoundedRectangle(cornerRadius:12)).overlay(RoundedRectangle(cornerRadius:12).stroke(panelBorder,lineWidth:1))
    }
    private var recordSheet: some View {
        NavigationStack {
            Form {
                Section("粘贴 FEN 局面") {
                    TextEditor(text:$fenText).frame(minHeight:90).font(.system(.caption,design:.monospaced))
                    Button("载入此局面") { model.importFEN(fenText); showsRecordSheet=false }
                        .disabled(fenText.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty)
                }
                Section("本机存档") {
                    if model.savedGames.isEmpty { Text("还没有保存的棋局").foregroundStyle(.secondary) }
                    ForEach(model.savedGames) { game in
                        Button { model.loadSavedGame(game); showsRecordSheet=false } label: {
                            VStack(alignment:.leading,spacing:2) {
                                Text(game.title); Text("\(game.moves.count) 半回合 · \(game.savedAt.formatted())").font(.caption2).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }.navigationTitle("载入棋谱或局面").toolbar { Button("完成") { showsRecordSheet=false } }
        }
    }
    private var setupControls: some View {
        VStack(alignment:.leading,spacing:8) {
            HStack {
                setupButton("移动",tool:.move); setupButton("删除",tool:.erase); Spacer()
                Button("完成摆盘") { model.finishSetup() }.buttonStyle(.borderedProminent).tint(green)
            }
            ForEach([ChessSide.white,.black],id:\.rawValue) { side in
                ScrollView(.horizontal,showsIndicators:false) {
                    HStack(spacing:6) {
                        Text(side.title).font(.caption).foregroundStyle(.secondary)
                        ForEach(PieceKind.allCases,id:\.rawValue) { kind in
                            setupButton(BoardPiece(side:side,kind:kind,file:0,rank:0).symbol,tool:.piece(side,kind))
                        }
                    }
                }
            }
            Text("摆盘时已暂停引擎；完成后将按当前执棋方重新分析。").font(.caption2).foregroundStyle(.secondary)
        }.padding(10).background(softCard,in:RoundedRectangle(cornerRadius:10)).overlay(RoundedRectangle(cornerRadius:10).stroke(panelBorder,lineWidth:1))
    }
    private func setupButton(_ title:String,tool:SetupTool)->some View {
        Button(title) { model.setSetupTool(tool) }.buttonStyle(.bordered).tint(model.setupTool == tool ? green : .gray)
    }
    private var settings:some View {
        VStack(spacing:12) {
            HStack { Text("分析深度 \(model.depth)"); Slider(value:Binding(get:{Double(model.depth)},set:{model.depth=Int($0);model.scheduleAnalysis()}),in:8...22,step:1) }
            Stepper("候选着法 \(model.multiPV)",value:Binding(get:{model.multiPV},set:{model.multiPV=$0;model.scheduleAnalysis()}),in:1...8)
            if model.gameMode == .computer {
                Picker("执棋",selection:Binding(get:{model.humanSide},set:{model.setHumanSide($0)})){Text("白方").tag(ChessSide.white);Text("黑方").tag(ChessSide.black)}.pickerStyle(.segmented)
                Picker("电脑等级", selection: Binding(get:{model.computerElo},set:{model.setComputerElo($0)})) {
                    ForEach(ComputerLevel.all) { level in
                        Text("\(level.name) · Elo \(level.elo)").tag(level.elo)
                    }
                }
            }
            Text("分析时仍可行棋；局面变化后会立即中断旧任务，并分析新局面。").font(.caption).foregroundStyle(.secondary)
        }.padding(14).background(card,in:RoundedRectangle(cornerRadius:12)).overlay(RoundedRectangle(cornerRadius:12).stroke(panelBorder,lineWidth:1))
    }
}
