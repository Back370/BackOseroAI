package p26x29;

import ap26.Move;

/**
 * 課題 1g 用の置換表（transposition table）。
 *
 * <p>α-β / NegaScout 探索では、異なる手順で同じ盤面（局面）に到達する
 * （= transposition）。同一局面を一度評価したら結果を保存し、次に同じ
 * 局面が現れたら再探索せずに使い回すことで、探索ノード数を削減する。
 *
 * <p>実装はゼロブリストハッシュ（{@link NegaScoutPlayer#zobrist}）を
 * 添字にした固定長配列（直接マッピング）である。サイズは 2 のべき乗とし、
 * 下位ビットマスクで添字を取る。衝突は「保存済みエントリより浅くなければ
 * 上書き（depth-preferred replacement）」で解決し、保存したハッシュ全体を
 * 突き合わせて誤った使い回しを防ぐ。
 *
 * <h2>エントリの種類（flag）</h2>
 * <ul>
 *   <li>{@link #EXACT}: α &lt; value &lt; β の正確な評価値。</li>
 *   <li>{@link #LOWER}: β カットで打ち切られた下界（true value ≥ value）。</li>
 *   <li>{@link #UPPER}: α を一度も更新できなかった上界（true value ≤ value）。</li>
 * </ul>
 */
public final class TranspositionTable {

  public static final int EXACT = 0;
  public static final int LOWER = 1;
  public static final int UPPER = 2;

  /** 1 局面分の保存内容。 */
  static final class Entry {
    long key;       // ゼロブリストハッシュ全体（衝突検証用）
    double value;   // 評価値
    int flag;       // EXACT / LOWER / UPPER
    int depth;      // この値を得たときの残り深さ（大きいほど信頼できる）
    Move best;      // 最善手（move ordering で最優先に試す）
    boolean used;   // 有効エントリか
  }

  final Entry[] table;
  final int mask;

  /** 容量 2^bits のエントリを確保する。bits=20 で約 100 万エントリ。 */
  public TranspositionTable(int bits) {
    int size = 1 << bits;
    this.table = new Entry[size];
    for (int i = 0; i < size; i++) this.table[i] = new Entry();
    this.mask = size - 1;
  }

  /** key の局面を引く。未登録または衝突なら null。 */
  Entry probe(long key) {
    Entry e = this.table[(int) (key & this.mask)];
    return (e.used && e.key == key) ? e : null;
  }

  /**
   * key の局面の探索結果を保存する。
   * 同じスロットに別局面があっても、今回の方が深い（または同局面）なら上書きする。
   */
  void store(long key, double value, int flag, int depth, Move best) {
    Entry e = this.table[(int) (key & this.mask)];
    if (!e.used || e.key == key || e.depth <= depth) {
      e.key = key;
      e.value = value;
      e.flag = flag;
      e.depth = depth;
      e.best = best;
      e.used = true;
    }
  }

  /** 全エントリを無効化する。 */
  public void clear() {
    for (Entry e : this.table) e.used = false;
  }
}
