import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = { title: "弈思 · 国际象棋教练", description: "Stockfish 18 驱动的行棋优先国际象棋分析教练。" };

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="zh-CN"><head><link rel="icon" href="/favicon-light.png" media="(prefers-color-scheme: light)"/><link rel="icon" href="/favicon-dark.png" media="(prefers-color-scheme: dark)"/><link rel="apple-touch-icon" href="/favicon-light.png"/></head><body>{children}</body></html>;
}
