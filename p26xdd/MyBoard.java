package p26xdd;

import static ap26.Color.BLOCK;
import static ap26.Color.BLACK;
import static ap26.Color.NONE;
import static ap26.Color.WHITE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import ap26.Board;
import ap26.Color;
import ap26.Move;

public class MyBoard implements Board, Cloneable {
  private final Color[] board;
  private Move move;

  public MyBoard() {
    this.board = Stream.generate(() -> NONE).limit(LENGTH).toArray(Color[]::new);
    this.move = Move.ofPass(NONE);
    init();
  }

  public MyBoard(Board source) {
    this.board = new Color[LENGTH];
    syncFrom(source);
  }

  private MyBoard(Color[] board, Move move) {
    this.board = Arrays.copyOf(board, board.length);
    this.move = move;
  }

  @Override
  public MyBoard clone() {
    return new MyBoard(this.board, this.move);
  }

  public void syncFrom(Board source) {
    for (int k = 0; k < LENGTH; k++) {
      this.board[k] = source.get(k);
    }
    Move sourceMove = source.getMove();
    this.move = sourceMove == null ? Move.ofPass(NONE) : sourceMove;
  }

  void init() {
    set(Move.parseIndex("c3"), BLACK);
    set(Move.parseIndex("d4"), BLACK);
    set(Move.parseIndex("d3"), WHITE);
    set(Move.parseIndex("c4"), WHITE);
  }

  @Override
  public Color get(int k) {
    return this.board[k];
  }

  @Override
  public Move getMove() {
    return this.move;
  }

  @Override
  public Color getTurn() {
    return this.move.isNone() ? BLACK : this.move.getColor().flipped();
  }

  public void set(int k, Color color) {
    this.board[k] = color;
  }

  @Override
  public boolean equals(Object otherObj) {
    if (otherObj instanceof MyBoard other) {
      return Arrays.equals(this.board, other.board);
    }
    return false;
  }

  @Override
  public int count(Color color) {
    return countAll().getOrDefault(color, 0L).intValue();
  }

  @Override
  public boolean isEnd() {
    var blackMoves = findNoPassLegalIndexes(BLACK);
    var whiteMoves = findNoPassLegalIndexes(WHITE);
    return blackMoves.isEmpty() && whiteMoves.isEmpty();
  }

  @Override
  public Color winner() {
    int value = score();
    if (!isEnd() || value == 0) {
      return NONE;
    }
    return value > 0 ? BLACK : WHITE;
  }

  @Override
  public void foul(Color color) {
    Color winner = color.flipped();
    IntStream.range(0, LENGTH).forEach(k -> this.board[k] = winner);
  }

  @Override
  public int score() {
    var counts = countAll();
    long blackCount = counts.getOrDefault(BLACK, 0L);
    long whiteCount = counts.getOrDefault(WHITE, 0L);
    long emptyCount = LENGTH - blackCount - whiteCount;
    int score = (int) (blackCount - whiteCount);

    if (blackCount == 0 || whiteCount == 0) {
      score += Integer.signum(score) * emptyCount;
    }

    return score;
  }

  Map<Color, Long> countAll() {
    return Arrays.stream(this.board).collect(
        Collectors.groupingBy(Function.identity(), Collectors.counting()));
  }

  @Override
  public List<Move> findLegalMoves(Color color) {
    return findLegalIndexes(color).stream()
        .map(k -> new Move(k, color))
        .toList();
  }

  List<Integer> findLegalIndexes(Color color) {
    var moves = findNoPassLegalIndexes(color);
    if (moves.isEmpty()) {
      moves.add(Move.PASS);
    }
    return moves;
  }

  List<Integer> findNoPassLegalIndexes(Color color) {
    var moves = new ArrayList<Integer>();
    for (int k = 0; k < LENGTH; k++) {
      if (this.board[k] != NONE) {
        continue;
      }
      for (var line : lines(k)) {
        if (!outflanked(line, color).isEmpty()) {
          moves.add(k);
        }
      }
    }
    return moves;
  }

  List<List<Integer>> lines(int k) {
    var lines = new ArrayList<List<Integer>>();
    for (int dir = 0; dir < 8; dir++) {
      lines.add(Move.line(k, dir));
    }
    return lines;
  }

  List<Move> outflanked(List<Integer> line, Color color) {
    if (line.size() <= 1) {
      return new ArrayList<Move>();
    }

    var flippables = new ArrayList<Move>();
    for (int k : line) {
      Color current = get(k);
      if (current == NONE || current == BLOCK) {
        break;
      }
      if (current == color) {
        return flippables;
      }
      flippables.add(new Move(k, color));
    }
    return new ArrayList<Move>();
  }

  @Override
  public MyBoard placed(Move move) {
    var copied = clone();
    copied.move = move;

    if (move.isPass() || move.isNone()) {
      return copied;
    }

    int index = move.getIndex();
    Color color = move.getColor();
    for (var line : copied.lines(index)) {
      for (var flipped : outflanked(line, color)) {
        copied.board[flipped.getIndex()] = color;
      }
    }
    copied.set(index, color);
    return copied;
  }

  @Override
  public MyBoard flipped() {
    var copied = clone();
    IntStream.range(0, LENGTH).forEach(k -> copied.board[k] = copied.board[k].flipped());
    copied.move = this.move.flipped();
    return copied;
  }
}
