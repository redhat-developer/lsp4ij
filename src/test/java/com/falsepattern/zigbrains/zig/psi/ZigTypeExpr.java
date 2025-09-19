// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.psi;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ZigTypeExpr extends ZigExpr {

  @NotNull
  ZigExpr getExpr();

  @NotNull
  List<ZigPrefixTypeOp> getPrefixTypeOpList();

}
