// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ZigCompareExpr extends ZigExpr {

  @Nullable
  ZigCompareOp getCompareOp();

  @NotNull
  List<ZigExpr> getExprList();

}
