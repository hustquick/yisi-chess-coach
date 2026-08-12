import AppKit

let arguments = CommandLine.arguments
guard arguments.count == 3 else {
    fatalError("Usage: swift make_app_icon.swift LIGHT_OUTPUT.png DARK_OUTPUT.png")
}

let canvasSize = NSSize(width: 1024, height: 1024)
let brandRed = NSColor(calibratedRed: 0.67, green: 0.10, blue: 0.08, alpha: 1)
let darkFrame = NSColor(calibratedWhite: 0.055, alpha: 1)

func roundedRect(_ rect: NSRect, radius: CGFloat, color: NSColor) {
    color.setFill()
    NSBezierPath(roundedRect: rect, xRadius: radius, yRadius: radius).fill()
}

func circle(center: NSPoint, radius: CGFloat, color: NSColor) {
    color.setFill()
    NSBezierPath(ovalIn: NSRect(
        x: center.x - radius, y: center.y - radius,
        width: radius * 2, height: radius * 2
    )).fill()
}

func drawPromotionMark(color: NSColor) {
    color.setFill()
    color.setStroke()

    // A crown appearing above a pawn makes promotion readable even at the
    // small size used by the Home Screen and App Library.
    let crown = NSBezierPath()
    crown.move(to: NSPoint(x: 300, y: 718))
    crown.line(to: NSPoint(x: 405, y: 620))
    crown.line(to: NSPoint(x: 512, y: 768))
    crown.line(to: NSPoint(x: 619, y: 620))
    crown.line(to: NSPoint(x: 724, y: 718))
    crown.line(to: NSPoint(x: 660, y: 514))
    crown.curve(to: NSPoint(x: 364, y: 514),
                controlPoint1: NSPoint(x: 610, y: 488),
                controlPoint2: NSPoint(x: 414, y: 488))
    crown.close()
    crown.lineWidth = 26
    crown.lineJoinStyle = .round
    crown.lineCapStyle = .round
    crown.stroke()
    circle(center: NSPoint(x: 300, y: 718), radius: 22, color: color)
    circle(center: NSPoint(x: 512, y: 768), radius: 22, color: color)
    circle(center: NSPoint(x: 724, y: 718), radius: 22, color: color)

    let rays: [(NSPoint, NSPoint)] = [
        (NSPoint(x: 512, y: 820), NSPoint(x: 512, y: 875)),
        (NSPoint(x: 370, y: 779), NSPoint(x: 335, y: 824)),
        (NSPoint(x: 654, y: 779), NSPoint(x: 689, y: 824)),
        (NSPoint(x: 270, y: 590), NSPoint(x: 218, y: 604)),
        (NSPoint(x: 754, y: 590), NSPoint(x: 806, y: 604))
    ]
    for (start, end) in rays {
        let ray = NSBezierPath()
        ray.move(to: start)
        ray.line(to: end)
        ray.lineWidth = 20
        ray.lineCapStyle = .round
        ray.stroke()
    }

    // Pawn silhouette, deliberately symmetric around the same 512 px axis as
    // the background so it remains optically centered after iOS masks the icon.
    circle(center: NSPoint(x: 512, y: 522), radius: 72, color: color)
    roundedRect(NSRect(x: 408, y: 430, width: 208, height: 45), radius: 22, color: color)

    let body = NSBezierPath()
    body.move(to: NSPoint(x: 450, y: 438))
    body.curve(to: NSPoint(x: 405, y: 292),
               controlPoint1: NSPoint(x: 449, y: 374),
               controlPoint2: NSPoint(x: 432, y: 324))
    body.line(to: NSPoint(x: 619, y: 292))
    body.curve(to: NSPoint(x: 574, y: 438),
               controlPoint1: NSPoint(x: 592, y: 324),
               controlPoint2: NSPoint(x: 575, y: 374))
    body.close()
    body.fill()

    roundedRect(NSRect(x: 368, y: 252, width: 288, height: 58), radius: 29, color: color)
    roundedRect(NSRect(x: 334, y: 190, width: 356, height: 68), radius: 34, color: color)
}

func render(output: String, frameColor: NSColor, innerColor: NSColor, markColor: NSColor) throws {
    guard let bitmap = NSBitmapImageRep(
        bitmapDataPlanes: nil,
        pixelsWide: 1024,
        pixelsHigh: 1024,
        bitsPerSample: 8,
        samplesPerPixel: 4,
        hasAlpha: true,
        isPlanar: false,
        colorSpaceName: .deviceRGB,
        bytesPerRow: 0,
        bitsPerPixel: 0
    ) else { fatalError("Could not create app icon bitmap") }
    bitmap.size = canvasSize

    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: bitmap)
    let canvas = NSRect(origin: .zero, size: canvasSize)
    frameColor.setFill()
    canvas.fill()

    // These dimensions exactly match the Xiangqi icon. Both light and dark
    // frames keep the same thickness, corner radius and optical weight.
    roundedRect(canvas.insetBy(dx: 106, dy: 106), radius: 154, color: innerColor)
    drawPromotionMark(color: markColor)
    NSGraphicsContext.restoreGraphicsState()

    guard let png = bitmap.representation(using: .png, properties: [:]) else {
        fatalError("Could not render app icon")
    }
    try png.write(to: URL(fileURLWithPath: output))
}

try render(
    output: arguments[1],
    frameColor: brandRed,
    innerColor: NSColor(calibratedRed: 0.98, green: 0.75, blue: 0.12, alpha: 1),
    markColor: brandRed
)
try render(
    output: arguments[2],
    frameColor: darkFrame,
    innerColor: NSColor(calibratedRed: 0.16, green: 0.045, blue: 0.055, alpha: 1),
    markColor: NSColor(calibratedRed: 1.00, green: 0.72, blue: 0.08, alpha: 1)
)
