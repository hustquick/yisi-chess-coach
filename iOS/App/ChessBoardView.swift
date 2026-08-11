import SwiftUI

struct ChessBoardView: View {
    @ObservedObject var viewModel: CoachViewModel
    private let light = Color(red: 0.92, green: 0.86, blue: 0.71)
    private let dark = Color(red: 0.33, green: 0.49, blue: 0.38)

    var body: some View {
        GeometryReader { geometry in
            let cell = geometry.size.width / 8
            ZStack(alignment: .topLeading) {
                ForEach(0..<64, id: \.self) { index in
                        let visualRank = index / 8
                        let visualFile = index % 8
                        let file = viewModel.boardFlipped ? 7-visualFile : visualFile
                        let rank = viewModel.boardFlipped ? 7-visualRank : visualRank
                        let square = squareName(file,rank)
                        Rectangle().fill((visualFile+visualRank).isMultiple(of: 2) ? light : dark)
                            .overlay {
                                if viewModel.legalTargets.contains(square) { Circle().fill(Color.blue.opacity(0.42)).frame(width: cell*0.28) }
                                if viewModel.selectedSquare == square { Rectangle().fill(Color.yellow.opacity(0.42)) }
                            }
                            .frame(width: cell,height: cell).offset(x:CGFloat(visualFile)*cell,y:CGFloat(visualRank)*cell)
                }
                if let move=viewModel.lastMove, move.count >= 4 {
                    ForEach([String(move.prefix(2)),String(move.dropFirst(2).prefix(2))],id:\.self) { square in
                        if let (f,r)=decode(square) {
                            let vf=viewModel.boardFlipped ? 7-f:f, vr=viewModel.boardFlipped ? 7-r:r
                            Rectangle().stroke(Color.orange,lineWidth:max(3,cell*0.05)).frame(width:cell,height:cell).offset(x:CGFloat(vf)*cell,y:CGFloat(vr)*cell)
                        }
                    }
                }
                if viewModel.showCandidateArrows { candidateArrows(cell:cell) }
                ForEach(viewModel.pieces) { piece in
                    let vf=viewModel.boardFlipped ? 7-piece.file:piece.file, vr=viewModel.boardFlipped ? 7-piece.rank:piece.rank
                    ChessPieceGlyph(piece:piece,size:cell*0.82)
                        .frame(width:cell,height:cell).offset(x:CGFloat(vf)*cell,y:CGFloat(vr)*cell).allowsHitTesting(false)
                }
                coordinateLabels(cell:cell)
                if viewModel.showCandidateArrows { candidateArrowLabels(cell:cell) }
            }
            .frame(width: geometry.size.width, height: geometry.size.height, alignment: .topLeading)
            .clipShape(RoundedRectangle(cornerRadius: 9)).shadow(color:.black.opacity(0.18),radius:10,y:5)
            .contentShape(Rectangle())
            .highPriorityGesture(SpatialTapGesture().onEnded { event in
                let vf=max(0,min(7,Int(event.location.x/cell))),vr=max(0,min(7,Int(event.location.y/cell)))
                viewModel.tap(file:viewModel.boardFlipped ? 7-vf:vf,rank:viewModel.boardFlipped ? 7-vr:vr)
            },including:.all)
        }
        .aspectRatio(1,contentMode:.fit)
        .accessibilityLabel("国际象棋棋盘")
    }

    @ViewBuilder private func coordinateLabels(cell:CGFloat)->some View {
        ForEach(0..<8,id:\.self) { i in
            let file=viewModel.boardFlipped ? 7-i:i, rank=viewModel.boardFlipped ? i+1:8-i
            Text(String(UnicodeScalar(97+file)!))
                .font(.caption2.bold())
                .foregroundStyle(i.isMultiple(of:2) ? light:dark)
                .shadow(color:.black.opacity(0.18),radius:0.5)
                .offset(x:CGFloat(i)*cell+4,y:7*cell+cell-16)
            Text("\(rank)")
                .font(.caption2.bold())
                .foregroundStyle(i.isMultiple(of:2) ? dark:light)
                .shadow(color:.black.opacity(0.18),radius:0.5)
                .offset(x:3,y:CGFloat(i)*cell+2)
        }
    }
    @ViewBuilder private func candidateArrowLabels(cell:CGFloat)->some View {
        ForEach(Array(viewModel.candidates.prefix(4).enumerated()),id:\.offset) { index,candidate in
            if candidate.move.count >= 4,
               let from=decode(String(candidate.move.prefix(2))),
               let to=decode(String(candidate.move.dropFirst(2).prefix(2))) {
                let ff=viewModel.boardFlipped ? 7-from.0:from.0
                let fr=viewModel.boardFlipped ? 7-from.1:from.1
                let tf=viewModel.boardFlipped ? 7-to.0:to.0
                let tr=viewModel.boardFlipped ? 7-to.1:to.1
                let x=(CGFloat(ff)+0.5+CGFloat(tf-ff)*0.34)*cell
                let y=(CGFloat(fr)+0.5+CGFloat(tr-fr)*0.34)*cell
                let color=[Color.green,.blue,.orange,.purple][index]
                Text("\(index+1)")
                    .font(.caption.bold())
                    .foregroundStyle(.white)
                    .frame(width:max(20,cell*0.31),height:max(20,cell*0.31))
                    .background(color,in:Circle())
                    .overlay(Circle().stroke(.white,lineWidth:1.5))
                    .shadow(color:.black.opacity(0.35),radius:2,y:1)
                    .position(x:x,y:y)
                    .allowsHitTesting(false)
            }
        }
    }
    private func candidateArrows(cell:CGFloat)->some View {
        Canvas { context,_ in
            for (index,candidate) in viewModel.candidates.prefix(4).enumerated() {
                guard candidate.move.count>=4,let from=decode(String(candidate.move.prefix(2))),let to=decode(String(candidate.move.dropFirst(2).prefix(2))) else{continue}
                let ff=viewModel.boardFlipped ? 7-from.0:from.0,fr=viewModel.boardFlipped ? 7-from.1:from.1,tf=viewModel.boardFlipped ? 7-to.0:to.0,tr=viewModel.boardFlipped ? 7-to.1:to.1
                let start=CGPoint(x:(CGFloat(ff)+0.5)*cell,y:(CGFloat(fr)+0.5)*cell),end=CGPoint(x:(CGFloat(tf)+0.5)*cell,y:(CGFloat(tr)+0.5)*cell)
                var path=Path();path.move(to:start);path.addLine(to:end);let color=[Color.green,.blue,.orange,.purple][index];context.stroke(path,with:.color(color.opacity(0.78)),style:StrokeStyle(lineWidth:max(4,cell*0.07),lineCap:.round))
                let angle=atan2(end.y-start.y,end.x-start.x),head=max(12,cell*0.18);var arrow=Path();arrow.move(to:end);arrow.addLine(to:CGPoint(x:end.x-head*cos(angle - 0.55),y:end.y-head*sin(angle - 0.55)));arrow.move(to:end);arrow.addLine(to:CGPoint(x:end.x-head*cos(angle + 0.55),y:end.y-head*sin(angle + 0.55)));context.stroke(arrow,with:.color(color),style:StrokeStyle(lineWidth:max(4,cell*0.07),lineCap:.round))
            }
        }.allowsHitTesting(false)
    }
    private func squareName(_ f:Int,_ r:Int)->String { "\(Character(UnicodeScalar(97+f)!))\(8-r)" }
    private func decode(_ s:String)->(Int,Int)? { let c=Array(s); guard c.count==2,let a=c[0].asciiValue,let n=c[1].wholeNumberValue else{return nil}; return(Int(a-97),8-n) }
}

/// Matches the HTML board's deliberately chosen Apple Symbols chess set.
/// The default SwiftUI system font substitutes a plainer text glyph on iOS,
/// so the two sides also lost the crisp white/black contrast of the web UI.
private struct ChessPieceGlyph: View {
    let piece: BoardPiece
    let size: CGFloat

    var body: some View {
        if piece.side == .white {
            Text(piece.symbol)
                .font(.custom("Apple Symbols",fixedSize:size))
                .foregroundStyle(.white)
                .shadow(color:.black.opacity(0.82),radius:0,x:1,y:0)
                .shadow(color:.black.opacity(0.82),radius:0,x:-1,y:0)
                .shadow(color:.black.opacity(0.82),radius:0,x:0,y:1)
                .shadow(color:.black.opacity(0.82),radius:0,x:0,y:-1)
                .shadow(color:.black.opacity(0.30),radius:1.5,x:0,y:2)
        } else {
            Text(piece.symbol)
                .font(.custom("Apple Symbols",fixedSize:size))
                .foregroundStyle(Color(red:0.075,green:0.075,blue:0.075))
                .shadow(color:.white.opacity(0.16),radius:0,x:0,y:-1)
                .shadow(color:.black.opacity(0.34),radius:1.5,x:0,y:2)
        }
    }
}
