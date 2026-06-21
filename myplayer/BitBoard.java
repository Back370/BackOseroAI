package myplayer;

import static ap26.Board.LENGTH;
import static ap26.Board.SIZE;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongUnaryOperator;

import ap26.Board;
import ap26.Color;

/**
 * 課題 1i 用。6×6 オセロ盤を 1 個の {@code long}（36 ビット）に詰め込み、
 * <b>ビット演算で着手可能位置の検出を一括（並列）処理</b>するビットボード。
 *
 * <h2>ビット配置</h2>
 * マス番号 {@code k = SIZE*row + col}（{@link ap26.Move} と同じ）をそのまま
 * ビット位置に使う。盤面は黒石・白石・壁（{@link Color#BLOCK}）の 3 つの
 * ビットマスクで表す。
 *
 * <h2>並列な合法手検出</h2>
 * {@link MyBoard#findNoPassLegalIndexes} は 36 マス × 8 方向をループで走査するが、
 * ビットボードでは「全マスを同時に」8 方向それぞれシフトして処理する。
 * 各方向 d について
 * <pre>
 *   c = shift_d(own) &amp; opp;           // 自石の d 隣が相手石
 *   c |= shift_d(c) &amp; opp; (相手石が続く間くり返す)
 *   moves |= shift_d(c) &amp; empty;      // その先が空マスなら着手可能
 * </pre>
 * を計算する。ループは「方向の数 8 × 盤の一辺 6」程度の固定回数で済み、
 * マスごとの逐次走査が消える。壁(BLOCK)は own/opp/empty のいずれにも
 * 含めないため、相手石の連結が壁で自然に途切れ、変形盤にも対応する。
 *
 * <h2>方向シフト（wrap 防止）</h2>
 * 横方向に動く成分があるシフトは、行をまたいで隣の列へ「回り込む」ビットを
 * 列マスク（{@link #COL0}/{@link #COL5}）で除去する。縦方向のはみ出しは
 * 盤マスク {@link #BOARD} で落とす。
 */
public final class BitBoard {

  static final int W = SIZE; // 6
  /** 36 ビットの盤マスク。 */
  static final long BOARD = (1L << LENGTH) - 1;

  static long colMask(int c) {
    long m = 0;
    for (int r = 0; r < W; r++) m |= 1L << (W * r + c);
    return m;
  }

  /** 左端列(a)・右端列(f)のマスク（横方向 wrap の除去用）。 */
  static final long COL0 = colMask(0);
  static final long COL5 = colMask(W - 1);

  long black, white, block;

  /** 既存 {@link Board} からビットボードを構築する。 */
  public static BitBoard from(Board b) {
    BitBoard bb = new BitBoard();
    for (int k = 0; k < LENGTH; k++) {
      Color c = b.get(k);
      if (c == Color.BLACK) bb.black |= 1L << k;
      else if (c == Color.WHITE) bb.white |= 1L << k;
      else if (c == Color.BLOCK) bb.block |= 1L << k;
    }
    return bb;
  }

  // --- 8 方向シフト（添字差分は ap26.Move.offsets と一致） ---
  static long e(long x)  { return (x << 1)       & ~COL0 & BOARD; } // col+1
  static long w(long x)  { return (x >>> 1)      & ~COL5; }         // col-1
  static long n(long x)  { return (x >>> W)      & BOARD; }         // row-1
  static long s(long x)  { return (x << W)       & BOARD; }         // row+1
  static long ne(long x) { return (x >>> (W - 1)) & ~COL0 & BOARD; } // col+1,row-1
  static long nw(long x) { return (x >>> (W + 1)) & ~COL5; }         // col-1,row-1
  static long se(long x) { return (x << (W + 1)) & ~COL0 & BOARD; }  // col+1,row+1
  static long sw(long x) { return (x << (W - 1)) & ~COL5 & BOARD; }  // col-1,row+1

  static final LongUnaryOperator[] DIRS =
      {BitBoard::e, BitBoard::w, BitBoard::n, BitBoard::s,
       BitBoard::ne, BitBoard::nw, BitBoard::se, BitBoard::sw};

  /** 1 方向分の合法手ビットを求める。 */
  static long genDir(long own, long opp, long empty, LongUnaryOperator sh) {
    long c = sh.applyAsLong(own) & opp;
    for (int i = 0; i < W - 2; i++) {
      c |= sh.applyAsLong(c) & opp;
    }
    return sh.applyAsLong(c) & empty;
  }

  /** color の合法手をビットマスクで一括算出する（パスは含まない）。 */
  public long legalMoves(Color color) {
    long own = color == Color.BLACK ? black : white;
    long opp = color == Color.BLACK ? white : black;
    long empty = ~(own | opp | block) & BOARD;
    long moves = 0;
    for (LongUnaryOperator sh : DIRS) {
      moves |= genDir(own, opp, empty, sh);
    }
    return moves;
  }

  /** ビットマスクを着手マス番号のリストへ展開する（昇順）。 */
  public static List<Integer> toIndexList(long bits) {
    List<Integer> ks = new ArrayList<>();
    while (bits != 0) {
      int k = Long.numberOfTrailingZeros(bits);
      ks.add(k);
      bits &= bits - 1; // 最下位の 1 ビットを消す
    }
    return ks;
  }
}
