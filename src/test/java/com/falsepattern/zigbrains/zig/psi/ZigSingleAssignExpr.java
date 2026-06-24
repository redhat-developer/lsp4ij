// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ZigSingleAssignExpr extends ZigExpr {

  @Nullable
  ZigAssignOp getAssignOp();

  @NotNull
  List<ZigExpr> getExprList();

}
