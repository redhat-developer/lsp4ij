// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ZigAdditionExpr extends ZigExpr {

  @NotNull
  List<ZigAdditionOp> getAdditionOpList();

  @NotNull
  List<ZigExpr> getExprList();

}
