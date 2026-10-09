// This is a generated file. Not intended for manual editing.
package com.falsepattern.zigbrains.zig.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LightPsiParser;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

import static com.falsepattern.zigbrains.zig.psi.ZigTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class ZigParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType root_, PsiBuilder builder_) {
    parseLight(root_, builder_);
    return builder_.getTreeBuilt();
  }

  public void parseLight(IElementType root_, PsiBuilder builder_) {
    boolean result_;
    builder_ = adapt_builder_(root_, builder_, this, EXTENDS_SETS_);
    Marker marker_ = enter_section_(builder_, 0, _COLLAPSE_, null);
    result_ = parse_root_(root_, builder_);
    exit_section_(builder_, 0, marker_, root_, result_, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType root_, PsiBuilder builder_) {
    return parse_root_(root_, builder_, 0);
  }

  static boolean parse_root_(IElementType root_, PsiBuilder builder_, int level_) {
    return Root(builder_, level_ + 1);
  }

  public static final TokenSet[] EXTENDS_SETS_ = new TokenSet[] {
    create_token_set_(ADDITION_EXPR, ASM_EXPR, ASSIGN_EXPR, BITWISE_EXPR,
      BIT_SHIFT_EXPR, BLOCK_EXPR, BOOL_AND_EXPR, BOOL_OR_EXPR,
      COMPARE_EXPR, CURLY_SUFFIX_EXPR, ERROR_UNION_EXPR, EXPR,
      FOR_EXPR, FOR_TYPE_EXPR, GROUPED_EXPR, IF_EXPR,
      IF_TYPE_EXPR, LABELED_TYPE_EXPR, LOOP_EXPR, LOOP_TYPE_EXPR,
      MULTIPLY_EXPR, PREFIX_EXPR, PRIMARY_EXPR, PRIMARY_TYPE_EXPR,
      SINGLE_ASSIGN_EXPR, SUFFIX_EXPR, SWITCH_EXPR, TYPE_EXPR,
      WHILE_CONTINUE_EXPR, WHILE_EXPR, WHILE_TYPE_EXPR),
  };

  /* ********************************************************** */
  // MultiplyExpr (AdditionOp MultiplyExpr)*
  public static boolean AdditionExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AdditionExpr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, ADDITION_EXPR, "<addition expr>");
    result_ = MultiplyExpr(builder_, level_ + 1);
    result_ = result_ && AdditionExpr_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (AdditionOp MultiplyExpr)*
  private static boolean AdditionExpr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AdditionExpr_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!AdditionExpr_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "AdditionExpr_1", pos_)) break;
    }
    return true;
  }

  // AdditionOp MultiplyExpr
  private static boolean AdditionExpr_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AdditionExpr_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = AdditionOp(builder_, level_ + 1);
    result_ = result_ && MultiplyExpr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // PLUS
  //   | MINUS
  //   | PLUS2
  //   | PLUSPERCENT
  //   | MINUSPERCENT
  //   | PLUSPIPE
  //   | MINUSPIPE
  public static boolean AdditionOp(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AdditionOp")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ADDITION_OP, "<addition op>");
    result_ = consumeToken(builder_, PLUS);
    if (!result_) result_ = consumeToken(builder_, MINUS);
    if (!result_) result_ = consumeToken(builder_, PLUS2);
    if (!result_) result_ = consumeToken(builder_, PLUSPERCENT);
    if (!result_) result_ = consumeToken(builder_, MINUSPERCENT);
    if (!result_) result_ = consumeToken(builder_, PLUSPIPE);
    if (!result_) result_ = consumeToken(builder_, MINUSPIPE);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // KEYWORD_ADDRSPACE LPAREN Expr RPAREN
  public static boolean AddrSpace(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AddrSpace")) return false;
    if (!nextTokenIs(builder_, KEYWORD_ADDRSPACE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, KEYWORD_ADDRSPACE, LPAREN);
    result_ = result_ && Expr(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, ADDR_SPACE, result_);
    return result_;
  }

  /* ********************************************************** */
  // LBRACKET Expr (COLON Expr)? RBRACKET
  public static boolean ArrayTypeStart(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ArrayTypeStart")) return false;
    if (!nextTokenIs(builder_, LBRACKET)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LBRACKET);
    result_ = result_ && Expr(builder_, level_ + 1);
    result_ = result_ && ArrayTypeStart_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACKET);
    exit_section_(builder_, marker_, ARRAY_TYPE_START, result_);
    return result_;
  }

  // (COLON Expr)?
  private static boolean ArrayTypeStart_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ArrayTypeStart_2")) return false;
    ArrayTypeStart_2_0(builder_, level_ + 1);
    return true;
  }

  // COLON Expr
  private static boolean ArrayTypeStart_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ArrayTypeStart_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && Expr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // COLON StringList
  public static boolean AsmClobbers(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AsmClobbers")) return false;
    if (!nextTokenIs(builder_, COLON)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && StringList(builder_, level_ + 1);
    exit_section_(builder_, marker_, ASM_CLOBBERS, result_);
    return result_;
  }

  /* ********************************************************** */
  // KEYWORD_ASM KEYWORD_VOLATILE? LPAREN Expr AsmOutput? RPAREN
  public static boolean AsmExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AsmExpr")) return false;
    if (!nextTokenIs(builder_, KEYWORD_ASM)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_ASM);
    result_ = result_ && AsmExpr_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, LPAREN);
    result_ = result_ && Expr(builder_, level_ + 1);
    result_ = result_ && AsmExpr_4(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, ASM_EXPR, result_);
    return result_;
  }

  // KEYWORD_VOLATILE?
  private static boolean AsmExpr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AsmExpr_1")) return false;
    consumeToken(builder_, KEYWORD_VOLATILE);
    return true;
  }

  // AsmOutput?
  private static boolean AsmExpr_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AsmExpr_4")) return false;
    AsmOutput(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // COLON AsmInputList AsmClobbers?
  public static boolean AsmInput(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AsmInput")) return false;
    if (!nextTokenIs(builder_, COLON)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && AsmInputList(builder_, level_ + 1);
    result_ = result_ && AsmInput_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, ASM_INPUT, result_);
    return result_;
  }

  // AsmClobbers?
  private static boolean AsmInput_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AsmInput_2")) return false;
    AsmClobbers(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // LBRACKET IDENTIFIER RBRACKET StringLiteral LPAREN Expr RPAREN
  public static boolean AsmInputItem(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AsmInputItem")) return false;
    if (!nextTokenIs(builder_, LBRACKET)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, LBRACKET, IDENTIFIER, RBRACKET);
    result_ = result_ && StringLiteral(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, LPAREN);
    result_ = result_ && Expr(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, ASM_INPUT_ITEM, result_);
    return result_;
  }

  /* ********************************************************** */
  // (AsmInputItem COMMA)* AsmInputItem?
  public static boolean AsmInputList(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AsmInputList")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ASM_INPUT_LIST, "<asm input list>");
    result_ = AsmInputList_0(builder_, level_ + 1);
    result_ = result_ && AsmInputList_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (AsmInputItem COMMA)*
  private static boolean AsmInputList_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AsmInputList_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!AsmInputList_0_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "AsmInputList_0", pos_)) break;
    }
    return true;
  }

  // AsmInputItem COMMA
  private static boolean AsmInputList_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AsmInputList_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = AsmInputItem(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COMMA);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // AsmInputItem?
  private static boolean AsmInputList_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AsmInputList_1")) return false;
    AsmInputItem(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // COLON AsmOutputList AsmInput?
  public static boolean AsmOutput(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AsmOutput")) return false;
    if (!nextTokenIs(builder_, COLON)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && AsmOutputList(builder_, level_ + 1);
    result_ = result_ && AsmOutput_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, ASM_OUTPUT, result_);
    return result_;
  }

  // AsmInput?
  private static boolean AsmOutput_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AsmOutput_2")) return false;
    AsmInput(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // LBRACKET IDENTIFIER RBRACKET StringLiteral LPAREN (MINUSRARROW TypeExpr | IDENTIFIER) RPAREN
  public static boolean AsmOutputItem(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AsmOutputItem")) return false;
    if (!nextTokenIs(builder_, LBRACKET)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, LBRACKET, IDENTIFIER, RBRACKET);
    result_ = result_ && StringLiteral(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, LPAREN);
    result_ = result_ && AsmOutputItem_5(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, ASM_OUTPUT_ITEM, result_);
    return result_;
  }

  // MINUSRARROW TypeExpr | IDENTIFIER
  private static boolean AsmOutputItem_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AsmOutputItem_5")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = AsmOutputItem_5_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, IDENTIFIER);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // MINUSRARROW TypeExpr
  private static boolean AsmOutputItem_5_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AsmOutputItem_5_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, MINUSRARROW);
    result_ = result_ && TypeExpr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // (AsmOutputItem COMMA)* AsmOutputItem?
  public static boolean AsmOutputList(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AsmOutputList")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ASM_OUTPUT_LIST, "<asm output list>");
    result_ = AsmOutputList_0(builder_, level_ + 1);
    result_ = result_ && AsmOutputList_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (AsmOutputItem COMMA)*
  private static boolean AsmOutputList_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AsmOutputList_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!AsmOutputList_0_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "AsmOutputList_0", pos_)) break;
    }
    return true;
  }

  // AsmOutputItem COMMA
  private static boolean AsmOutputList_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AsmOutputList_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = AsmOutputItem(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COMMA);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // AsmOutputItem?
  private static boolean AsmOutputList_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AsmOutputList_1")) return false;
    AsmOutputItem(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // Expr (AssignOp Expr | (COMMA Expr)+ EQUAL Expr)?
  public static boolean AssignExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AssignExpr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, ASSIGN_EXPR, "<assign expr>");
    result_ = Expr(builder_, level_ + 1);
    result_ = result_ && AssignExpr_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (AssignOp Expr | (COMMA Expr)+ EQUAL Expr)?
  private static boolean AssignExpr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AssignExpr_1")) return false;
    AssignExpr_1_0(builder_, level_ + 1);
    return true;
  }

  // AssignOp Expr | (COMMA Expr)+ EQUAL Expr
  private static boolean AssignExpr_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AssignExpr_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = AssignExpr_1_0_0(builder_, level_ + 1);
    if (!result_) result_ = AssignExpr_1_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // AssignOp Expr
  private static boolean AssignExpr_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AssignExpr_1_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = AssignOp(builder_, level_ + 1);
    result_ = result_ && Expr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (COMMA Expr)+ EQUAL Expr
  private static boolean AssignExpr_1_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AssignExpr_1_0_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = AssignExpr_1_0_1_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, EQUAL);
    result_ = result_ && Expr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (COMMA Expr)+
  private static boolean AssignExpr_1_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AssignExpr_1_0_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = AssignExpr_1_0_1_0_0(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!AssignExpr_1_0_1_0_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "AssignExpr_1_0_1_0", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // COMMA Expr
  private static boolean AssignExpr_1_0_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AssignExpr_1_0_1_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && Expr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // ASTERISKEQUAL
  //   | ASTERISKPIPEEQUAL
  //   | SLASHEQUAL
  //   | PERCENTEQUAL
  //   | PLUSEQUAL
  //   | PLUSPIPEEQUAL
  //   | MINUSEQUAL
  //   | MINUSPIPEEQUAL
  //   | LARROW2EQUAL
  //   | LARROW2PIPEEQUAL
  //   | RARROW2EQUAL
  //   | AMPERSANDEQUAL
  //   | CARETEQUAL
  //   | PIPEEQUAL
  //   | ASTERISKPERCENTEQUAL
  //   | PLUSPERCENTEQUAL
  //   | MINUSPERCENTEQUAL
  //   | EQUAL
  public static boolean AssignOp(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "AssignOp")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ASSIGN_OP, "<assign op>");
    result_ = consumeToken(builder_, ASTERISKEQUAL);
    if (!result_) result_ = consumeToken(builder_, ASTERISKPIPEEQUAL);
    if (!result_) result_ = consumeToken(builder_, SLASHEQUAL);
    if (!result_) result_ = consumeToken(builder_, PERCENTEQUAL);
    if (!result_) result_ = consumeToken(builder_, PLUSEQUAL);
    if (!result_) result_ = consumeToken(builder_, PLUSPIPEEQUAL);
    if (!result_) result_ = consumeToken(builder_, MINUSEQUAL);
    if (!result_) result_ = consumeToken(builder_, MINUSPIPEEQUAL);
    if (!result_) result_ = consumeToken(builder_, LARROW2EQUAL);
    if (!result_) result_ = consumeToken(builder_, LARROW2PIPEEQUAL);
    if (!result_) result_ = consumeToken(builder_, RARROW2EQUAL);
    if (!result_) result_ = consumeToken(builder_, AMPERSANDEQUAL);
    if (!result_) result_ = consumeToken(builder_, CARETEQUAL);
    if (!result_) result_ = consumeToken(builder_, PIPEEQUAL);
    if (!result_) result_ = consumeToken(builder_, ASTERISKPERCENTEQUAL);
    if (!result_) result_ = consumeToken(builder_, PLUSPERCENTEQUAL);
    if (!result_) result_ = consumeToken(builder_, MINUSPERCENTEQUAL);
    if (!result_) result_ = consumeToken(builder_, EQUAL);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // AdditionExpr (BitShiftOp AdditionExpr)*
  public static boolean BitShiftExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "BitShiftExpr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, BIT_SHIFT_EXPR, "<bit shift expr>");
    result_ = AdditionExpr(builder_, level_ + 1);
    result_ = result_ && BitShiftExpr_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (BitShiftOp AdditionExpr)*
  private static boolean BitShiftExpr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "BitShiftExpr_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!BitShiftExpr_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "BitShiftExpr_1", pos_)) break;
    }
    return true;
  }

  // BitShiftOp AdditionExpr
  private static boolean BitShiftExpr_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "BitShiftExpr_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = BitShiftOp(builder_, level_ + 1);
    result_ = result_ && AdditionExpr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // LARROW2
  //   | RARROW2
  //   | LARROW2PIPE
  public static boolean BitShiftOp(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "BitShiftOp")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, BIT_SHIFT_OP, "<bit shift op>");
    result_ = consumeToken(builder_, LARROW2);
    if (!result_) result_ = consumeToken(builder_, RARROW2);
    if (!result_) result_ = consumeToken(builder_, LARROW2PIPE);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // BitShiftExpr (BitwiseOp BitShiftExpr)*
  public static boolean BitwiseExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "BitwiseExpr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, BITWISE_EXPR, "<bitwise expr>");
    result_ = BitShiftExpr(builder_, level_ + 1);
    result_ = result_ && BitwiseExpr_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (BitwiseOp BitShiftExpr)*
  private static boolean BitwiseExpr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "BitwiseExpr_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!BitwiseExpr_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "BitwiseExpr_1", pos_)) break;
    }
    return true;
  }

  // BitwiseOp BitShiftExpr
  private static boolean BitwiseExpr_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "BitwiseExpr_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = BitwiseOp(builder_, level_ + 1);
    result_ = result_ && BitShiftExpr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // AMPERSAND
  //   | CARET
  //   | PIPE
  //   | KEYWORD_ORELSE
  //   | KEYWORD_CATCH Payload?
  public static boolean BitwiseOp(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "BitwiseOp")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, BITWISE_OP, "<bitwise op>");
    result_ = consumeToken(builder_, AMPERSAND);
    if (!result_) result_ = consumeToken(builder_, CARET);
    if (!result_) result_ = consumeToken(builder_, PIPE);
    if (!result_) result_ = consumeToken(builder_, KEYWORD_ORELSE);
    if (!result_) result_ = BitwiseOp_4(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // KEYWORD_CATCH Payload?
  private static boolean BitwiseOp_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "BitwiseOp_4")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_CATCH);
    result_ = result_ && BitwiseOp_4_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // Payload?
  private static boolean BitwiseOp_4_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "BitwiseOp_4_1")) return false;
    Payload(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // LBRACE ZB_Block_Statement RBRACE
  public static boolean Block(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "Block")) return false;
    if (!nextTokenIs(builder_, LBRACE)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, BLOCK, null);
    result_ = consumeToken(builder_, LBRACE);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, ZB_Block_Statement(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, RBRACE) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // BlockLabel? Block
  public static boolean BlockExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "BlockExpr")) return false;
    if (!nextTokenIs(builder_, "<block expr>", IDENTIFIER, LBRACE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, BLOCK_EXPR, "<block expr>");
    result_ = BlockExpr_0(builder_, level_ + 1);
    result_ = result_ && Block(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // BlockLabel?
  private static boolean BlockExpr_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "BlockExpr_0")) return false;
    BlockLabel(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // BlockExpr
  //   | ZB_BlockExprStatement_AssignExpr
  public static boolean BlockExprStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "BlockExprStatement")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, BLOCK_EXPR_STATEMENT, "<block expr statement>");
    result_ = BlockExpr(builder_, level_ + 1);
    if (!result_) result_ = ZB_BlockExprStatement_AssignExpr(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // IDENTIFIER COLON
  public static boolean BlockLabel(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "BlockLabel")) return false;
    if (!nextTokenIs(builder_, IDENTIFIER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, IDENTIFIER, COLON);
    exit_section_(builder_, marker_, BLOCK_LABEL, result_);
    return result_;
  }

  /* ********************************************************** */
  // CompareExpr (KEYWORD_AND CompareExpr)*
  public static boolean BoolAndExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "BoolAndExpr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, BOOL_AND_EXPR, "<bool and expr>");
    result_ = CompareExpr(builder_, level_ + 1);
    result_ = result_ && BoolAndExpr_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (KEYWORD_AND CompareExpr)*
  private static boolean BoolAndExpr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "BoolAndExpr_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!BoolAndExpr_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "BoolAndExpr_1", pos_)) break;
    }
    return true;
  }

  // KEYWORD_AND CompareExpr
  private static boolean BoolAndExpr_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "BoolAndExpr_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_AND);
    result_ = result_ && CompareExpr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // BoolAndExpr (KEYWORD_OR BoolAndExpr)*
  public static boolean BoolOrExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "BoolOrExpr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, BOOL_OR_EXPR, "<bool or expr>");
    result_ = BoolAndExpr(builder_, level_ + 1);
    result_ = result_ && BoolOrExpr_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (KEYWORD_OR BoolAndExpr)*
  private static boolean BoolOrExpr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "BoolOrExpr_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!BoolOrExpr_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "BoolOrExpr_1", pos_)) break;
    }
    return true;
  }

  // KEYWORD_OR BoolAndExpr
  private static boolean BoolOrExpr_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "BoolOrExpr_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_OR);
    result_ = result_ && BoolAndExpr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // COLON IDENTIFIER
  public static boolean BreakLabel(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "BreakLabel")) return false;
    if (!nextTokenIs(builder_, COLON)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, COLON, IDENTIFIER);
    exit_section_(builder_, marker_, BREAK_LABEL, result_);
    return result_;
  }

  /* ********************************************************** */
  // KEYWORD_ALIGN LPAREN Expr RPAREN
  public static boolean ByteAlign(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ByteAlign")) return false;
    if (!nextTokenIs(builder_, KEYWORD_ALIGN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, KEYWORD_ALIGN, LPAREN);
    result_ = result_ && Expr(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, BYTE_ALIGN, result_);
    return result_;
  }

  /* ********************************************************** */
  // KEYWORD_CALLCONV LPAREN Expr RPAREN
  public static boolean CallConv(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "CallConv")) return false;
    if (!nextTokenIs(builder_, KEYWORD_CALLCONV)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, KEYWORD_CALLCONV, LPAREN);
    result_ = result_ && Expr(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, CALL_CONV, result_);
    return result_;
  }

  /* ********************************************************** */
  // BitwiseExpr (CompareOp BitwiseExpr)?
  public static boolean CompareExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "CompareExpr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, COMPARE_EXPR, "<compare expr>");
    result_ = BitwiseExpr(builder_, level_ + 1);
    result_ = result_ && CompareExpr_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (CompareOp BitwiseExpr)?
  private static boolean CompareExpr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "CompareExpr_1")) return false;
    CompareExpr_1_0(builder_, level_ + 1);
    return true;
  }

  // CompareOp BitwiseExpr
  private static boolean CompareExpr_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "CompareExpr_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = CompareOp(builder_, level_ + 1);
    result_ = result_ && BitwiseExpr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // EQUALEQUAL
  //   | EXCLAMATIONMARKEQUAL
  //   | LARROW
  //   | RARROW
  //   | LARROWEQUAL
  //   | RARROWEQUAL
  public static boolean CompareOp(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "CompareOp")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, COMPARE_OP, "<compare op>");
    result_ = consumeToken(builder_, EQUALEQUAL);
    if (!result_) result_ = consumeToken(builder_, EXCLAMATIONMARKEQUAL);
    if (!result_) result_ = consumeToken(builder_, LARROW);
    if (!result_) result_ = consumeToken(builder_, RARROW);
    if (!result_) result_ = consumeToken(builder_, LARROWEQUAL);
    if (!result_) result_ = consumeToken(builder_, RARROWEQUAL);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // KEYWORD_COMPTIME Block
  public static boolean ComptimeDecl(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ComptimeDecl")) return false;
    if (!nextTokenIs(builder_, KEYWORD_COMPTIME)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_COMPTIME);
    result_ = result_ && Block(builder_, level_ + 1);
    exit_section_(builder_, marker_, COMPTIME_DECL, result_);
    return result_;
  }

  /* ********************************************************** */
  // BlockExpr
  //   | VarDeclExprStatement
  public static boolean ComptimeStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ComptimeStatement")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, COMPTIME_STATEMENT, "<comptime statement>");
    result_ = BlockExpr(builder_, level_ + 1);
    if (!result_) result_ = VarDeclExprStatement(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // (KEYWORD_EXTERN | KEYWORD_PACKED)? ContainerDeclAuto
  public static boolean ContainerDecl(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerDecl")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, CONTAINER_DECL, "<container decl>");
    result_ = ContainerDecl_0(builder_, level_ + 1);
    result_ = result_ && ContainerDeclAuto(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (KEYWORD_EXTERN | KEYWORD_PACKED)?
  private static boolean ContainerDecl_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerDecl_0")) return false;
    ContainerDecl_0_0(builder_, level_ + 1);
    return true;
  }

  // KEYWORD_EXTERN | KEYWORD_PACKED
  private static boolean ContainerDecl_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerDecl_0_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, KEYWORD_EXTERN);
    if (!result_) result_ = consumeToken(builder_, KEYWORD_PACKED);
    return result_;
  }

  /* ********************************************************** */
  // ContainerDeclType LBRACE CONTAINER_DOC_COMMENT? ZB_ContainerDeclAuto_ContainerMembers RBRACE
  public static boolean ContainerDeclAuto(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerDeclAuto")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, CONTAINER_DECL_AUTO, "<container decl auto>");
    result_ = ContainerDeclType(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, LBRACE);
    pinned_ = result_; // pin = 2
    result_ = result_ && report_error_(builder_, ContainerDeclAuto_2(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, ZB_ContainerDeclAuto_ContainerMembers(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, RBRACE) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // CONTAINER_DOC_COMMENT?
  private static boolean ContainerDeclAuto_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerDeclAuto_2")) return false;
    consumeToken(builder_, CONTAINER_DOC_COMMENT);
    return true;
  }

  /* ********************************************************** */
  // KEYWORD_STRUCT (LPAREN ZB_ContainerDeclType_Expr RPAREN)?
  //   | KEYWORD_OPAQUE
  //   | KEYWORD_ENUM (LPAREN ZB_ContainerDeclType_Expr RPAREN)?
  //   | KEYWORD_UNION (LPAREN (KEYWORD_ENUM (LPAREN ZB_ContainerDeclType_Expr RPAREN)? | ZB_ContainerDeclType_Expr) RPAREN)?
  public static boolean ContainerDeclType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerDeclType")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, CONTAINER_DECL_TYPE, "<container decl type>");
    result_ = ContainerDeclType_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, KEYWORD_OPAQUE);
    if (!result_) result_ = ContainerDeclType_2(builder_, level_ + 1);
    if (!result_) result_ = ContainerDeclType_3(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // KEYWORD_STRUCT (LPAREN ZB_ContainerDeclType_Expr RPAREN)?
  private static boolean ContainerDeclType_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerDeclType_0")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = consumeToken(builder_, KEYWORD_STRUCT);
    pinned_ = result_; // pin = 1
    result_ = result_ && ContainerDeclType_0_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // (LPAREN ZB_ContainerDeclType_Expr RPAREN)?
  private static boolean ContainerDeclType_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerDeclType_0_1")) return false;
    ContainerDeclType_0_1_0(builder_, level_ + 1);
    return true;
  }

  // LPAREN ZB_ContainerDeclType_Expr RPAREN
  private static boolean ContainerDeclType_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerDeclType_0_1_0")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = consumeToken(builder_, LPAREN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, ZB_ContainerDeclType_Expr(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, RPAREN) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // KEYWORD_ENUM (LPAREN ZB_ContainerDeclType_Expr RPAREN)?
  private static boolean ContainerDeclType_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerDeclType_2")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = consumeToken(builder_, KEYWORD_ENUM);
    pinned_ = result_; // pin = 1
    result_ = result_ && ContainerDeclType_2_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // (LPAREN ZB_ContainerDeclType_Expr RPAREN)?
  private static boolean ContainerDeclType_2_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerDeclType_2_1")) return false;
    ContainerDeclType_2_1_0(builder_, level_ + 1);
    return true;
  }

  // LPAREN ZB_ContainerDeclType_Expr RPAREN
  private static boolean ContainerDeclType_2_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerDeclType_2_1_0")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = consumeToken(builder_, LPAREN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, ZB_ContainerDeclType_Expr(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, RPAREN) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // KEYWORD_UNION (LPAREN (KEYWORD_ENUM (LPAREN ZB_ContainerDeclType_Expr RPAREN)? | ZB_ContainerDeclType_Expr) RPAREN)?
  private static boolean ContainerDeclType_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerDeclType_3")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = consumeToken(builder_, KEYWORD_UNION);
    pinned_ = result_; // pin = 1
    result_ = result_ && ContainerDeclType_3_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // (LPAREN (KEYWORD_ENUM (LPAREN ZB_ContainerDeclType_Expr RPAREN)? | ZB_ContainerDeclType_Expr) RPAREN)?
  private static boolean ContainerDeclType_3_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerDeclType_3_1")) return false;
    ContainerDeclType_3_1_0(builder_, level_ + 1);
    return true;
  }

  // LPAREN (KEYWORD_ENUM (LPAREN ZB_ContainerDeclType_Expr RPAREN)? | ZB_ContainerDeclType_Expr) RPAREN
  private static boolean ContainerDeclType_3_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerDeclType_3_1_0")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = consumeToken(builder_, LPAREN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, ContainerDeclType_3_1_0_1(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, RPAREN) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // KEYWORD_ENUM (LPAREN ZB_ContainerDeclType_Expr RPAREN)? | ZB_ContainerDeclType_Expr
  private static boolean ContainerDeclType_3_1_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerDeclType_3_1_0_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ContainerDeclType_3_1_0_1_0(builder_, level_ + 1);
    if (!result_) result_ = ZB_ContainerDeclType_Expr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // KEYWORD_ENUM (LPAREN ZB_ContainerDeclType_Expr RPAREN)?
  private static boolean ContainerDeclType_3_1_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerDeclType_3_1_0_1_0")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = consumeToken(builder_, KEYWORD_ENUM);
    pinned_ = result_; // pin = 1
    result_ = result_ && ContainerDeclType_3_1_0_1_0_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // (LPAREN ZB_ContainerDeclType_Expr RPAREN)?
  private static boolean ContainerDeclType_3_1_0_1_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerDeclType_3_1_0_1_0_1")) return false;
    ContainerDeclType_3_1_0_1_0_1_0(builder_, level_ + 1);
    return true;
  }

  // LPAREN ZB_ContainerDeclType_Expr RPAREN
  private static boolean ContainerDeclType_3_1_0_1_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerDeclType_3_1_0_1_0_1_0")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = consumeToken(builder_, LPAREN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, ZB_ContainerDeclType_Expr(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, RPAREN) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // TestDecl | ComptimeDecl | DOC_COMMENT? KEYWORD_PUB? Decl
  public static boolean ContainerDeclaration(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerDeclaration")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, CONTAINER_DECLARATION, "<container declaration>");
    result_ = TestDecl(builder_, level_ + 1);
    if (!result_) result_ = ComptimeDecl(builder_, level_ + 1);
    if (!result_) result_ = ContainerDeclaration_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // DOC_COMMENT? KEYWORD_PUB? Decl
  private static boolean ContainerDeclaration_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerDeclaration_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ContainerDeclaration_2_0(builder_, level_ + 1);
    result_ = result_ && ContainerDeclaration_2_1(builder_, level_ + 1);
    result_ = result_ && Decl(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // DOC_COMMENT?
  private static boolean ContainerDeclaration_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerDeclaration_2_0")) return false;
    consumeToken(builder_, DOC_COMMENT);
    return true;
  }

  // KEYWORD_PUB?
  private static boolean ContainerDeclaration_2_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerDeclaration_2_1")) return false;
    consumeToken(builder_, KEYWORD_PUB);
    return true;
  }

  /* ********************************************************** */
  // DOC_COMMENT? KEYWORD_COMPTIME? !KEYWORD_FN (IDENTIFIER COLON)? TypeExpr ByteAlign? (EQUAL Expr)?
  public static boolean ContainerField(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerField")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, CONTAINER_FIELD, "<container field>");
    result_ = ContainerField_0(builder_, level_ + 1);
    result_ = result_ && ContainerField_1(builder_, level_ + 1);
    result_ = result_ && ContainerField_2(builder_, level_ + 1);
    result_ = result_ && ContainerField_3(builder_, level_ + 1);
    result_ = result_ && TypeExpr(builder_, level_ + 1);
    pinned_ = result_; // pin = 5
    result_ = result_ && report_error_(builder_, ContainerField_5(builder_, level_ + 1));
    result_ = pinned_ && ContainerField_6(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // DOC_COMMENT?
  private static boolean ContainerField_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerField_0")) return false;
    consumeToken(builder_, DOC_COMMENT);
    return true;
  }

  // KEYWORD_COMPTIME?
  private static boolean ContainerField_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerField_1")) return false;
    consumeToken(builder_, KEYWORD_COMPTIME);
    return true;
  }

  // !KEYWORD_FN
  private static boolean ContainerField_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerField_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !consumeToken(builder_, KEYWORD_FN);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (IDENTIFIER COLON)?
  private static boolean ContainerField_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerField_3")) return false;
    ContainerField_3_0(builder_, level_ + 1);
    return true;
  }

  // IDENTIFIER COLON
  private static boolean ContainerField_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerField_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, IDENTIFIER, COLON);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ByteAlign?
  private static boolean ContainerField_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerField_5")) return false;
    ByteAlign(builder_, level_ + 1);
    return true;
  }

  // (EQUAL Expr)?
  private static boolean ContainerField_6(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerField_6")) return false;
    ContainerField_6_0(builder_, level_ + 1);
    return true;
  }

  // EQUAL Expr
  private static boolean ContainerField_6_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerField_6_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, EQUAL);
    result_ = result_ && Expr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // ContainerDeclaration* (ContainerField COMMA)* (ContainerField | ContainerDeclaration*)
  public static boolean ContainerMembers(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerMembers")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, CONTAINER_MEMBERS, "<container members>");
    result_ = ContainerMembers_0(builder_, level_ + 1);
    result_ = result_ && ContainerMembers_1(builder_, level_ + 1);
    result_ = result_ && ContainerMembers_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // ContainerDeclaration*
  private static boolean ContainerMembers_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerMembers_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!ContainerDeclaration(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "ContainerMembers_0", pos_)) break;
    }
    return true;
  }

  // (ContainerField COMMA)*
  private static boolean ContainerMembers_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerMembers_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!ContainerMembers_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "ContainerMembers_1", pos_)) break;
    }
    return true;
  }

  // ContainerField COMMA
  private static boolean ContainerMembers_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerMembers_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ContainerField(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COMMA);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ContainerField | ContainerDeclaration*
  private static boolean ContainerMembers_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerMembers_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ContainerField(builder_, level_ + 1);
    if (!result_) result_ = ContainerMembers_2_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ContainerDeclaration*
  private static boolean ContainerMembers_2_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ContainerMembers_2_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!ContainerDeclaration(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "ContainerMembers_2_1", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // TypeExpr InitList?
  public static boolean CurlySuffixExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "CurlySuffixExpr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, CURLY_SUFFIX_EXPR, "<curly suffix expr>");
    result_ = TypeExpr(builder_, level_ + 1);
    result_ = result_ && CurlySuffixExpr_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // InitList?
  private static boolean CurlySuffixExpr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "CurlySuffixExpr_1")) return false;
    InitList(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // (KEYWORD_EXPORT | KEYWORD_EXTERN STRING_LITERAL_SINGLE? | KEYWORD_INLINE | KEYWORD_NOINLINE)? FnProto (SEMICOLON | Block)
  //   | (KEYWORD_EXPORT | KEYWORD_EXTERN STRING_LITERAL_SINGLE?)? KEYWORD_THREADLOCAL? GlobalVarDecl
  //   | KEYWORD_USINGNAMESPACE Expr SEMICOLON
  public static boolean Decl(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "Decl")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, DECL, "<decl>");
    result_ = Decl_0(builder_, level_ + 1);
    if (!result_) result_ = Decl_1(builder_, level_ + 1);
    if (!result_) result_ = Decl_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (KEYWORD_EXPORT | KEYWORD_EXTERN STRING_LITERAL_SINGLE? | KEYWORD_INLINE | KEYWORD_NOINLINE)? FnProto (SEMICOLON | Block)
  private static boolean Decl_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "Decl_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = Decl_0_0(builder_, level_ + 1);
    result_ = result_ && FnProto(builder_, level_ + 1);
    result_ = result_ && Decl_0_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (KEYWORD_EXPORT | KEYWORD_EXTERN STRING_LITERAL_SINGLE? | KEYWORD_INLINE | KEYWORD_NOINLINE)?
  private static boolean Decl_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "Decl_0_0")) return false;
    Decl_0_0_0(builder_, level_ + 1);
    return true;
  }

  // KEYWORD_EXPORT | KEYWORD_EXTERN STRING_LITERAL_SINGLE? | KEYWORD_INLINE | KEYWORD_NOINLINE
  private static boolean Decl_0_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "Decl_0_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_EXPORT);
    if (!result_) result_ = Decl_0_0_0_1(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, KEYWORD_INLINE);
    if (!result_) result_ = consumeToken(builder_, KEYWORD_NOINLINE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // KEYWORD_EXTERN STRING_LITERAL_SINGLE?
  private static boolean Decl_0_0_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "Decl_0_0_0_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_EXTERN);
    result_ = result_ && Decl_0_0_0_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // STRING_LITERAL_SINGLE?
  private static boolean Decl_0_0_0_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "Decl_0_0_0_1_1")) return false;
    consumeToken(builder_, STRING_LITERAL_SINGLE);
    return true;
  }

  // SEMICOLON | Block
  private static boolean Decl_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "Decl_0_2")) return false;
    boolean result_;
    result_ = consumeToken(builder_, SEMICOLON);
    if (!result_) result_ = Block(builder_, level_ + 1);
    return result_;
  }

  // (KEYWORD_EXPORT | KEYWORD_EXTERN STRING_LITERAL_SINGLE?)? KEYWORD_THREADLOCAL? GlobalVarDecl
  private static boolean Decl_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "Decl_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = Decl_1_0(builder_, level_ + 1);
    result_ = result_ && Decl_1_1(builder_, level_ + 1);
    result_ = result_ && GlobalVarDecl(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (KEYWORD_EXPORT | KEYWORD_EXTERN STRING_LITERAL_SINGLE?)?
  private static boolean Decl_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "Decl_1_0")) return false;
    Decl_1_0_0(builder_, level_ + 1);
    return true;
  }

  // KEYWORD_EXPORT | KEYWORD_EXTERN STRING_LITERAL_SINGLE?
  private static boolean Decl_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "Decl_1_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_EXPORT);
    if (!result_) result_ = Decl_1_0_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // KEYWORD_EXTERN STRING_LITERAL_SINGLE?
  private static boolean Decl_1_0_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "Decl_1_0_0_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_EXTERN);
    result_ = result_ && Decl_1_0_0_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // STRING_LITERAL_SINGLE?
  private static boolean Decl_1_0_0_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "Decl_1_0_0_1_1")) return false;
    consumeToken(builder_, STRING_LITERAL_SINGLE);
    return true;
  }

  // KEYWORD_THREADLOCAL?
  private static boolean Decl_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "Decl_1_1")) return false;
    consumeToken(builder_, KEYWORD_THREADLOCAL);
    return true;
  }

  // KEYWORD_USINGNAMESPACE Expr SEMICOLON
  private static boolean Decl_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "Decl_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_USINGNAMESPACE);
    result_ = result_ && Expr(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, SEMICOLON);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // KEYWORD_ERROR LBRACE IdentifierList RBRACE
  public static boolean ErrorSetDecl(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ErrorSetDecl")) return false;
    if (!nextTokenIs(builder_, KEYWORD_ERROR)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, KEYWORD_ERROR, LBRACE);
    result_ = result_ && IdentifierList(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACE);
    exit_section_(builder_, marker_, ERROR_SET_DECL, result_);
    return result_;
  }

  /* ********************************************************** */
  // SuffixExpr (EXCLAMATIONMARK TypeExpr)?
  public static boolean ErrorUnionExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ErrorUnionExpr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, ERROR_UNION_EXPR, "<error union expr>");
    result_ = SuffixExpr(builder_, level_ + 1);
    result_ = result_ && ErrorUnionExpr_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (EXCLAMATIONMARK TypeExpr)?
  private static boolean ErrorUnionExpr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ErrorUnionExpr_1")) return false;
    ErrorUnionExpr_1_0(builder_, level_ + 1);
    return true;
  }

  // EXCLAMATIONMARK TypeExpr
  private static boolean ErrorUnionExpr_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ErrorUnionExpr_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, EXCLAMATIONMARK);
    result_ = result_ && TypeExpr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // BoolOrExpr
  public static boolean Expr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "Expr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, EXPR, "<expr>");
    result_ = BoolOrExpr(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // (ZB_ExprList_Expr COMMA)* ZB_ExprList_Expr?
  public static boolean ExprList(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ExprList")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, EXPR_LIST, "<expr list>");
    result_ = ExprList_0(builder_, level_ + 1);
    result_ = result_ && ExprList_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (ZB_ExprList_Expr COMMA)*
  private static boolean ExprList_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ExprList_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!ExprList_0_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "ExprList_0", pos_)) break;
    }
    return true;
  }

  // ZB_ExprList_Expr COMMA
  private static boolean ExprList_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ExprList_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ZB_ExprList_Expr(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COMMA);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ZB_ExprList_Expr?
  private static boolean ExprList_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ExprList_1")) return false;
    ZB_ExprList_Expr(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // DOT IDENTIFIER EQUAL Expr
  public static boolean FieldInit(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "FieldInit")) return false;
    if (!nextTokenIs(builder_, DOT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, DOT, IDENTIFIER, EQUAL);
    result_ = result_ && Expr(builder_, level_ + 1);
    exit_section_(builder_, marker_, FIELD_INIT, result_);
    return result_;
  }

  /* ********************************************************** */
  // LPAREN ExprList RPAREN
  public static boolean FnCallArguments(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "FnCallArguments")) return false;
    if (!nextTokenIs(builder_, LPAREN)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FN_CALL_ARGUMENTS, null);
    result_ = consumeToken(builder_, LPAREN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, ExprList(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, RPAREN) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // KEYWORD_FN IDENTIFIER? LPAREN ParamDeclList RPAREN ByteAlign? AddrSpace? LinkSection? CallConv? EXCLAMATIONMARK? TypeExpr
  public static boolean FnProto(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "FnProto")) return false;
    if (!nextTokenIs(builder_, KEYWORD_FN)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FN_PROTO, null);
    result_ = consumeToken(builder_, KEYWORD_FN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, FnProto_1(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, consumeToken(builder_, LPAREN)) && result_;
    result_ = pinned_ && report_error_(builder_, ParamDeclList(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, consumeToken(builder_, RPAREN)) && result_;
    result_ = pinned_ && report_error_(builder_, FnProto_5(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, FnProto_6(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, FnProto_7(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, FnProto_8(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, FnProto_9(builder_, level_ + 1)) && result_;
    result_ = pinned_ && TypeExpr(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // IDENTIFIER?
  private static boolean FnProto_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "FnProto_1")) return false;
    consumeToken(builder_, IDENTIFIER);
    return true;
  }

  // ByteAlign?
  private static boolean FnProto_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "FnProto_5")) return false;
    ByteAlign(builder_, level_ + 1);
    return true;
  }

  // AddrSpace?
  private static boolean FnProto_6(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "FnProto_6")) return false;
    AddrSpace(builder_, level_ + 1);
    return true;
  }

  // LinkSection?
  private static boolean FnProto_7(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "FnProto_7")) return false;
    LinkSection(builder_, level_ + 1);
    return true;
  }

  // CallConv?
  private static boolean FnProto_8(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "FnProto_8")) return false;
    CallConv(builder_, level_ + 1);
    return true;
  }

  // EXCLAMATIONMARK?
  private static boolean FnProto_9(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "FnProto_9")) return false;
    consumeToken(builder_, EXCLAMATIONMARK);
    return true;
  }

  /* ********************************************************** */
  // ForPrefix Expr (KEYWORD_ELSE Expr)?
  public static boolean ForExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ForExpr")) return false;
    if (!nextTokenIs(builder_, KEYWORD_FOR)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ForPrefix(builder_, level_ + 1);
    result_ = result_ && Expr(builder_, level_ + 1);
    result_ = result_ && ForExpr_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, FOR_EXPR, result_);
    return result_;
  }

  // (KEYWORD_ELSE Expr)?
  private static boolean ForExpr_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ForExpr_2")) return false;
    ForExpr_2_0(builder_, level_ + 1);
    return true;
  }

  // KEYWORD_ELSE Expr
  private static boolean ForExpr_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ForExpr_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_ELSE);
    result_ = result_ && Expr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // Expr (DOT2 Expr?)?
  public static boolean ForInput(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ForInput")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FOR_INPUT, "<for input>");
    result_ = Expr(builder_, level_ + 1);
    result_ = result_ && ForInput_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, ZigParser::ZB_ForInput_Recover);
    return result_;
  }

  // (DOT2 Expr?)?
  private static boolean ForInput_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ForInput_1")) return false;
    ForInput_1_0(builder_, level_ + 1);
    return true;
  }

  // DOT2 Expr?
  private static boolean ForInput_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ForInput_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOT2);
    result_ = result_ && ForInput_1_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // Expr?
  private static boolean ForInput_1_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ForInput_1_0_1")) return false;
    Expr(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // PIPE ZB_ForPayload_Item (COMMA ZB_ForPayload_Item)* PIPE
  public static boolean ForPayload(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ForPayload")) return false;
    if (!nextTokenIs(builder_, PIPE)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FOR_PAYLOAD, null);
    result_ = consumeToken(builder_, PIPE);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, ZB_ForPayload_Item(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, ForPayload_2(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, PIPE) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // (COMMA ZB_ForPayload_Item)*
  private static boolean ForPayload_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ForPayload_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!ForPayload_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "ForPayload_2", pos_)) break;
    }
    return true;
  }

  // COMMA ZB_ForPayload_Item
  private static boolean ForPayload_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ForPayload_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && ZB_ForPayload_Item(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // KEYWORD_FOR LPAREN ZB_ForParams RPAREN ForPayload
  public static boolean ForPrefix(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ForPrefix")) return false;
    if (!nextTokenIs(builder_, KEYWORD_FOR)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FOR_PREFIX, null);
    result_ = consumeTokens(builder_, 1, KEYWORD_FOR, LPAREN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, ZB_ForParams(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, consumeToken(builder_, RPAREN)) && result_;
    result_ = pinned_ && ForPayload(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // ForPrefix ZB_ForStatement_Body
  public static boolean ForStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ForStatement")) return false;
    if (!nextTokenIs(builder_, KEYWORD_FOR)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FOR_STATEMENT, null);
    result_ = ForPrefix(builder_, level_ + 1);
    pinned_ = result_; // pin = 1
    result_ = result_ && ZB_ForStatement_Body(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // ForPrefix TypeExpr (KEYWORD_ELSE TypeExpr)?
  public static boolean ForTypeExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ForTypeExpr")) return false;
    if (!nextTokenIs(builder_, KEYWORD_FOR)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ForPrefix(builder_, level_ + 1);
    result_ = result_ && TypeExpr(builder_, level_ + 1);
    result_ = result_ && ForTypeExpr_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, FOR_TYPE_EXPR, result_);
    return result_;
  }

  // (KEYWORD_ELSE TypeExpr)?
  private static boolean ForTypeExpr_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ForTypeExpr_2")) return false;
    ForTypeExpr_2_0(builder_, level_ + 1);
    return true;
  }

  // KEYWORD_ELSE TypeExpr
  private static boolean ForTypeExpr_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ForTypeExpr_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_ELSE);
    result_ = result_ && TypeExpr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // VarDeclProto (EQUAL Expr)? SEMICOLON
  public static boolean GlobalVarDecl(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "GlobalVarDecl")) return false;
    if (!nextTokenIs(builder_, "<global var decl>", KEYWORD_CONST, KEYWORD_VAR)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, GLOBAL_VAR_DECL, "<global var decl>");
    result_ = VarDeclProto(builder_, level_ + 1);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, GlobalVarDecl_1(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, SEMICOLON) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // (EQUAL Expr)?
  private static boolean GlobalVarDecl_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "GlobalVarDecl_1")) return false;
    GlobalVarDecl_1_0(builder_, level_ + 1);
    return true;
  }

  // EQUAL Expr
  private static boolean GlobalVarDecl_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "GlobalVarDecl_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, EQUAL);
    result_ = result_ && Expr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // LPAREN Expr RPAREN
  public static boolean GroupedExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "GroupedExpr")) return false;
    if (!nextTokenIs(builder_, LPAREN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && Expr(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, GROUPED_EXPR, result_);
    return result_;
  }

  /* ********************************************************** */
  // (DOC_COMMENT? IDENTIFIER COMMA)* (DOC_COMMENT? IDENTIFIER)?
  public static boolean IdentifierList(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "IdentifierList")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, IDENTIFIER_LIST, "<identifier list>");
    result_ = IdentifierList_0(builder_, level_ + 1);
    result_ = result_ && IdentifierList_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (DOC_COMMENT? IDENTIFIER COMMA)*
  private static boolean IdentifierList_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "IdentifierList_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!IdentifierList_0_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "IdentifierList_0", pos_)) break;
    }
    return true;
  }

  // DOC_COMMENT? IDENTIFIER COMMA
  private static boolean IdentifierList_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "IdentifierList_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = IdentifierList_0_0_0(builder_, level_ + 1);
    result_ = result_ && consumeTokens(builder_, 0, IDENTIFIER, COMMA);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // DOC_COMMENT?
  private static boolean IdentifierList_0_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "IdentifierList_0_0_0")) return false;
    consumeToken(builder_, DOC_COMMENT);
    return true;
  }

  // (DOC_COMMENT? IDENTIFIER)?
  private static boolean IdentifierList_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "IdentifierList_1")) return false;
    IdentifierList_1_0(builder_, level_ + 1);
    return true;
  }

  // DOC_COMMENT? IDENTIFIER
  private static boolean IdentifierList_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "IdentifierList_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = IdentifierList_1_0_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, IDENTIFIER);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // DOC_COMMENT?
  private static boolean IdentifierList_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "IdentifierList_1_0_0")) return false;
    consumeToken(builder_, DOC_COMMENT);
    return true;
  }

  /* ********************************************************** */
  // IfPrefix Expr (KEYWORD_ELSE Payload? Expr)?
  public static boolean IfExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "IfExpr")) return false;
    if (!nextTokenIs(builder_, KEYWORD_IF)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = IfPrefix(builder_, level_ + 1);
    result_ = result_ && Expr(builder_, level_ + 1);
    result_ = result_ && IfExpr_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, IF_EXPR, result_);
    return result_;
  }

  // (KEYWORD_ELSE Payload? Expr)?
  private static boolean IfExpr_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "IfExpr_2")) return false;
    IfExpr_2_0(builder_, level_ + 1);
    return true;
  }

  // KEYWORD_ELSE Payload? Expr
  private static boolean IfExpr_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "IfExpr_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_ELSE);
    result_ = result_ && IfExpr_2_0_1(builder_, level_ + 1);
    result_ = result_ && Expr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // Payload?
  private static boolean IfExpr_2_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "IfExpr_2_0_1")) return false;
    Payload(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // KEYWORD_IF ZB_IfPrefix_Operand PtrPayload?
  public static boolean IfPrefix(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "IfPrefix")) return false;
    if (!nextTokenIs(builder_, KEYWORD_IF)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, IF_PREFIX, null);
    result_ = consumeToken(builder_, KEYWORD_IF);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, ZB_IfPrefix_Operand(builder_, level_ + 1));
    result_ = pinned_ && IfPrefix_2(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // PtrPayload?
  private static boolean IfPrefix_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "IfPrefix_2")) return false;
    PtrPayload(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // IfPrefix ZB_IfStatement_Body
  public static boolean IfStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "IfStatement")) return false;
    if (!nextTokenIs(builder_, KEYWORD_IF)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, IF_STATEMENT, null);
    result_ = IfPrefix(builder_, level_ + 1);
    pinned_ = result_; // pin = 1
    result_ = result_ && ZB_IfStatement_Body(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // IfPrefix TypeExpr (KEYWORD_ELSE Payload? TypeExpr)?
  public static boolean IfTypeExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "IfTypeExpr")) return false;
    if (!nextTokenIs(builder_, KEYWORD_IF)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = IfPrefix(builder_, level_ + 1);
    result_ = result_ && TypeExpr(builder_, level_ + 1);
    result_ = result_ && IfTypeExpr_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, IF_TYPE_EXPR, result_);
    return result_;
  }

  // (KEYWORD_ELSE Payload? TypeExpr)?
  private static boolean IfTypeExpr_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "IfTypeExpr_2")) return false;
    IfTypeExpr_2_0(builder_, level_ + 1);
    return true;
  }

  // KEYWORD_ELSE Payload? TypeExpr
  private static boolean IfTypeExpr_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "IfTypeExpr_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_ELSE);
    result_ = result_ && IfTypeExpr_2_0_1(builder_, level_ + 1);
    result_ = result_ && TypeExpr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // Payload?
  private static boolean IfTypeExpr_2_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "IfTypeExpr_2_0_1")) return false;
    Payload(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // LBRACE ZB_InitList_Body RBRACE
  public static boolean InitList(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "InitList")) return false;
    if (!nextTokenIs(builder_, LBRACE)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, INIT_LIST, null);
    result_ = consumeToken(builder_, LBRACE);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, ZB_InitList_Body(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, RBRACE) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // BlockLabel? (Block | LoopStatement | SwitchExpr)
  public static boolean LabeledStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "LabeledStatement")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, LABELED_STATEMENT, "<labeled statement>");
    result_ = LabeledStatement_0(builder_, level_ + 1);
    result_ = result_ && LabeledStatement_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // BlockLabel?
  private static boolean LabeledStatement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "LabeledStatement_0")) return false;
    BlockLabel(builder_, level_ + 1);
    return true;
  }

  // Block | LoopStatement | SwitchExpr
  private static boolean LabeledStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "LabeledStatement_1")) return false;
    boolean result_;
    result_ = Block(builder_, level_ + 1);
    if (!result_) result_ = LoopStatement(builder_, level_ + 1);
    if (!result_) result_ = SwitchExpr(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // BlockLabel Block
  //   | BlockLabel? LoopTypeExpr
  //   | BlockLabel? SwitchExpr
  public static boolean LabeledTypeExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "LabeledTypeExpr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, LABELED_TYPE_EXPR, "<labeled type expr>");
    result_ = LabeledTypeExpr_0(builder_, level_ + 1);
    if (!result_) result_ = LabeledTypeExpr_1(builder_, level_ + 1);
    if (!result_) result_ = LabeledTypeExpr_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // BlockLabel Block
  private static boolean LabeledTypeExpr_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "LabeledTypeExpr_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = BlockLabel(builder_, level_ + 1);
    result_ = result_ && Block(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // BlockLabel? LoopTypeExpr
  private static boolean LabeledTypeExpr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "LabeledTypeExpr_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = LabeledTypeExpr_1_0(builder_, level_ + 1);
    result_ = result_ && LoopTypeExpr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // BlockLabel?
  private static boolean LabeledTypeExpr_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "LabeledTypeExpr_1_0")) return false;
    BlockLabel(builder_, level_ + 1);
    return true;
  }

  // BlockLabel? SwitchExpr
  private static boolean LabeledTypeExpr_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "LabeledTypeExpr_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = LabeledTypeExpr_2_0(builder_, level_ + 1);
    result_ = result_ && SwitchExpr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // BlockLabel?
  private static boolean LabeledTypeExpr_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "LabeledTypeExpr_2_0")) return false;
    BlockLabel(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // KEYWORD_LINKSECTION LPAREN Expr RPAREN
  public static boolean LinkSection(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "LinkSection")) return false;
    if (!nextTokenIs(builder_, KEYWORD_LINKSECTION)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, KEYWORD_LINKSECTION, LPAREN);
    result_ = result_ && Expr(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, LINK_SECTION, result_);
    return result_;
  }

  /* ********************************************************** */
  // KEYWORD_INLINE? (ForExpr | WhileExpr)
  public static boolean LoopExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "LoopExpr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, LOOP_EXPR, "<loop expr>");
    result_ = LoopExpr_0(builder_, level_ + 1);
    result_ = result_ && LoopExpr_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // KEYWORD_INLINE?
  private static boolean LoopExpr_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "LoopExpr_0")) return false;
    consumeToken(builder_, KEYWORD_INLINE);
    return true;
  }

  // ForExpr | WhileExpr
  private static boolean LoopExpr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "LoopExpr_1")) return false;
    boolean result_;
    result_ = ForExpr(builder_, level_ + 1);
    if (!result_) result_ = WhileExpr(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // KEYWORD_INLINE? (ForStatement | WhileStatement)
  public static boolean LoopStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "LoopStatement")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, LOOP_STATEMENT, "<loop statement>");
    result_ = LoopStatement_0(builder_, level_ + 1);
    result_ = result_ && LoopStatement_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // KEYWORD_INLINE?
  private static boolean LoopStatement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "LoopStatement_0")) return false;
    consumeToken(builder_, KEYWORD_INLINE);
    return true;
  }

  // ForStatement | WhileStatement
  private static boolean LoopStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "LoopStatement_1")) return false;
    boolean result_;
    result_ = ForStatement(builder_, level_ + 1);
    if (!result_) result_ = WhileStatement(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // KEYWORD_INLINE? (ForTypeExpr | WhileTypeExpr)
  public static boolean LoopTypeExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "LoopTypeExpr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, LOOP_TYPE_EXPR, "<loop type expr>");
    result_ = LoopTypeExpr_0(builder_, level_ + 1);
    result_ = result_ && LoopTypeExpr_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // KEYWORD_INLINE?
  private static boolean LoopTypeExpr_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "LoopTypeExpr_0")) return false;
    consumeToken(builder_, KEYWORD_INLINE);
    return true;
  }

  // ForTypeExpr | WhileTypeExpr
  private static boolean LoopTypeExpr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "LoopTypeExpr_1")) return false;
    boolean result_;
    result_ = ForTypeExpr(builder_, level_ + 1);
    if (!result_) result_ = WhileTypeExpr(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // PrefixExpr (MultiplyOp PrefixExpr)*
  public static boolean MultiplyExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "MultiplyExpr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, MULTIPLY_EXPR, "<multiply expr>");
    result_ = PrefixExpr(builder_, level_ + 1);
    result_ = result_ && MultiplyExpr_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (MultiplyOp PrefixExpr)*
  private static boolean MultiplyExpr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "MultiplyExpr_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!MultiplyExpr_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "MultiplyExpr_1", pos_)) break;
    }
    return true;
  }

  // MultiplyOp PrefixExpr
  private static boolean MultiplyExpr_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "MultiplyExpr_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = MultiplyOp(builder_, level_ + 1);
    result_ = result_ && PrefixExpr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // PIPE2
  //   | ASTERISK
  //   | SLASH
  //   | PERCENT
  //   | ASTERISK2
  //   | ASTERISKPERCENT
  //   | ASTERISKPIPE
  public static boolean MultiplyOp(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "MultiplyOp")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, MULTIPLY_OP, "<multiply op>");
    result_ = consumeToken(builder_, PIPE2);
    if (!result_) result_ = consumeToken(builder_, ASTERISK);
    if (!result_) result_ = consumeToken(builder_, SLASH);
    if (!result_) result_ = consumeToken(builder_, PERCENT);
    if (!result_) result_ = consumeToken(builder_, ASTERISK2);
    if (!result_) result_ = consumeToken(builder_, ASTERISKPERCENT);
    if (!result_) result_ = consumeToken(builder_, ASTERISKPIPE);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // DOC_COMMENT? (KEYWORD_NOALIAS | KEYWORD_COMPTIME)? (IDENTIFIER COLON)? ParamType
  //   | DOT3
  public static boolean ParamDecl(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ParamDecl")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PARAM_DECL, "<param decl>");
    result_ = ParamDecl_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, DOT3);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // DOC_COMMENT? (KEYWORD_NOALIAS | KEYWORD_COMPTIME)? (IDENTIFIER COLON)? ParamType
  private static boolean ParamDecl_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ParamDecl_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ParamDecl_0_0(builder_, level_ + 1);
    result_ = result_ && ParamDecl_0_1(builder_, level_ + 1);
    result_ = result_ && ParamDecl_0_2(builder_, level_ + 1);
    result_ = result_ && ParamType(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // DOC_COMMENT?
  private static boolean ParamDecl_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ParamDecl_0_0")) return false;
    consumeToken(builder_, DOC_COMMENT);
    return true;
  }

  // (KEYWORD_NOALIAS | KEYWORD_COMPTIME)?
  private static boolean ParamDecl_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ParamDecl_0_1")) return false;
    ParamDecl_0_1_0(builder_, level_ + 1);
    return true;
  }

  // KEYWORD_NOALIAS | KEYWORD_COMPTIME
  private static boolean ParamDecl_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ParamDecl_0_1_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, KEYWORD_NOALIAS);
    if (!result_) result_ = consumeToken(builder_, KEYWORD_COMPTIME);
    return result_;
  }

  // (IDENTIFIER COLON)?
  private static boolean ParamDecl_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ParamDecl_0_2")) return false;
    ParamDecl_0_2_0(builder_, level_ + 1);
    return true;
  }

  // IDENTIFIER COLON
  private static boolean ParamDecl_0_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ParamDecl_0_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, IDENTIFIER, COLON);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // (ParamDecl COMMA)* ParamDecl?
  public static boolean ParamDeclList(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ParamDeclList")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PARAM_DECL_LIST, "<param decl list>");
    result_ = ParamDeclList_0(builder_, level_ + 1);
    result_ = result_ && ParamDeclList_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (ParamDecl COMMA)*
  private static boolean ParamDeclList_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ParamDeclList_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!ParamDeclList_0_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "ParamDeclList_0", pos_)) break;
    }
    return true;
  }

  // ParamDecl COMMA
  private static boolean ParamDeclList_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ParamDeclList_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ParamDecl(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COMMA);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ParamDecl?
  private static boolean ParamDeclList_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ParamDeclList_1")) return false;
    ParamDecl(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // KEYWORD_ANYTYPE
  //   | TypeExpr
  public static boolean ParamType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ParamType")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PARAM_TYPE, "<param type>");
    result_ = consumeToken(builder_, KEYWORD_ANYTYPE);
    if (!result_) result_ = TypeExpr(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // PIPE IDENTIFIER PIPE
  public static boolean Payload(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "Payload")) return false;
    if (!nextTokenIs(builder_, PIPE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, PIPE, IDENTIFIER, PIPE);
    exit_section_(builder_, marker_, PAYLOAD, result_);
    return result_;
  }

  /* ********************************************************** */
  // PrefixOp* PrimaryExpr
  public static boolean PrefixExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrefixExpr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, PREFIX_EXPR, "<prefix expr>");
    result_ = PrefixExpr_0(builder_, level_ + 1);
    result_ = result_ && PrimaryExpr(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // PrefixOp*
  private static boolean PrefixExpr_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrefixExpr_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!PrefixOp(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "PrefixExpr_0", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // EXCLAMATIONMARK
  //   | MINUS
  //   | TILDE
  //   | MINUSPERCENT
  //   | AMPERSAND
  //   | KEYWORD_TRY
  //   | KEYWORD_AWAIT
  public static boolean PrefixOp(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrefixOp")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PREFIX_OP, "<prefix op>");
    result_ = consumeToken(builder_, EXCLAMATIONMARK);
    if (!result_) result_ = consumeToken(builder_, MINUS);
    if (!result_) result_ = consumeToken(builder_, TILDE);
    if (!result_) result_ = consumeToken(builder_, MINUSPERCENT);
    if (!result_) result_ = consumeToken(builder_, AMPERSAND);
    if (!result_) result_ = consumeToken(builder_, KEYWORD_TRY);
    if (!result_) result_ = consumeToken(builder_, KEYWORD_AWAIT);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // QUESTIONMARK
  //   | KEYWORD_ANYFRAME MINUSRARROW
  //   | SliceTypeStart (ByteAlign | AddrSpace | KEYWORD_CONST | KEYWORD_VOLATILE | KEYWORD_ALLOWZERO)*
  //   | PtrTypeStart (AddrSpace | KEYWORD_ALIGN LPAREN Expr (COLON Expr COLON Expr)? RPAREN | KEYWORD_CONST | KEYWORD_VOLATILE | KEYWORD_ALLOWZERO)*
  //   | ArrayTypeStart
  public static boolean PrefixTypeOp(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrefixTypeOp")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PREFIX_TYPE_OP, "<prefix type op>");
    result_ = consumeToken(builder_, QUESTIONMARK);
    if (!result_) result_ = parseTokens(builder_, 0, KEYWORD_ANYFRAME, MINUSRARROW);
    if (!result_) result_ = PrefixTypeOp_2(builder_, level_ + 1);
    if (!result_) result_ = PrefixTypeOp_3(builder_, level_ + 1);
    if (!result_) result_ = ArrayTypeStart(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // SliceTypeStart (ByteAlign | AddrSpace | KEYWORD_CONST | KEYWORD_VOLATILE | KEYWORD_ALLOWZERO)*
  private static boolean PrefixTypeOp_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrefixTypeOp_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = SliceTypeStart(builder_, level_ + 1);
    result_ = result_ && PrefixTypeOp_2_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (ByteAlign | AddrSpace | KEYWORD_CONST | KEYWORD_VOLATILE | KEYWORD_ALLOWZERO)*
  private static boolean PrefixTypeOp_2_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrefixTypeOp_2_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!PrefixTypeOp_2_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "PrefixTypeOp_2_1", pos_)) break;
    }
    return true;
  }

  // ByteAlign | AddrSpace | KEYWORD_CONST | KEYWORD_VOLATILE | KEYWORD_ALLOWZERO
  private static boolean PrefixTypeOp_2_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrefixTypeOp_2_1_0")) return false;
    boolean result_;
    result_ = ByteAlign(builder_, level_ + 1);
    if (!result_) result_ = AddrSpace(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, KEYWORD_CONST);
    if (!result_) result_ = consumeToken(builder_, KEYWORD_VOLATILE);
    if (!result_) result_ = consumeToken(builder_, KEYWORD_ALLOWZERO);
    return result_;
  }

  // PtrTypeStart (AddrSpace | KEYWORD_ALIGN LPAREN Expr (COLON Expr COLON Expr)? RPAREN | KEYWORD_CONST | KEYWORD_VOLATILE | KEYWORD_ALLOWZERO)*
  private static boolean PrefixTypeOp_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrefixTypeOp_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = PtrTypeStart(builder_, level_ + 1);
    result_ = result_ && PrefixTypeOp_3_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (AddrSpace | KEYWORD_ALIGN LPAREN Expr (COLON Expr COLON Expr)? RPAREN | KEYWORD_CONST | KEYWORD_VOLATILE | KEYWORD_ALLOWZERO)*
  private static boolean PrefixTypeOp_3_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrefixTypeOp_3_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!PrefixTypeOp_3_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "PrefixTypeOp_3_1", pos_)) break;
    }
    return true;
  }

  // AddrSpace | KEYWORD_ALIGN LPAREN Expr (COLON Expr COLON Expr)? RPAREN | KEYWORD_CONST | KEYWORD_VOLATILE | KEYWORD_ALLOWZERO
  private static boolean PrefixTypeOp_3_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrefixTypeOp_3_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = AddrSpace(builder_, level_ + 1);
    if (!result_) result_ = PrefixTypeOp_3_1_0_1(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, KEYWORD_CONST);
    if (!result_) result_ = consumeToken(builder_, KEYWORD_VOLATILE);
    if (!result_) result_ = consumeToken(builder_, KEYWORD_ALLOWZERO);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // KEYWORD_ALIGN LPAREN Expr (COLON Expr COLON Expr)? RPAREN
  private static boolean PrefixTypeOp_3_1_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrefixTypeOp_3_1_0_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, KEYWORD_ALIGN, LPAREN);
    result_ = result_ && Expr(builder_, level_ + 1);
    result_ = result_ && PrefixTypeOp_3_1_0_1_3(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (COLON Expr COLON Expr)?
  private static boolean PrefixTypeOp_3_1_0_1_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrefixTypeOp_3_1_0_1_3")) return false;
    PrefixTypeOp_3_1_0_1_3_0(builder_, level_ + 1);
    return true;
  }

  // COLON Expr COLON Expr
  private static boolean PrefixTypeOp_3_1_0_1_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrefixTypeOp_3_1_0_1_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && Expr(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    result_ = result_ && Expr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // AsmExpr
  //   | IfExpr
  //   | KEYWORD_BREAK BreakLabel? Expr?
  //   | KEYWORD_COMPTIME Expr
  //   | KEYWORD_NOSUSPEND Expr
  //   | KEYWORD_CONTINUE BreakLabel? Expr?
  //   | KEYWORD_RESUME Expr
  //   | KEYWORD_RETURN Expr?
  //   | BlockLabel? LoopExpr
  //   | Block
  //   | CurlySuffixExpr
  public static boolean PrimaryExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrimaryExpr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, PRIMARY_EXPR, "<primary expr>");
    result_ = AsmExpr(builder_, level_ + 1);
    if (!result_) result_ = IfExpr(builder_, level_ + 1);
    if (!result_) result_ = PrimaryExpr_2(builder_, level_ + 1);
    if (!result_) result_ = PrimaryExpr_3(builder_, level_ + 1);
    if (!result_) result_ = PrimaryExpr_4(builder_, level_ + 1);
    if (!result_) result_ = PrimaryExpr_5(builder_, level_ + 1);
    if (!result_) result_ = PrimaryExpr_6(builder_, level_ + 1);
    if (!result_) result_ = PrimaryExpr_7(builder_, level_ + 1);
    if (!result_) result_ = PrimaryExpr_8(builder_, level_ + 1);
    if (!result_) result_ = Block(builder_, level_ + 1);
    if (!result_) result_ = CurlySuffixExpr(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // KEYWORD_BREAK BreakLabel? Expr?
  private static boolean PrimaryExpr_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrimaryExpr_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_BREAK);
    result_ = result_ && PrimaryExpr_2_1(builder_, level_ + 1);
    result_ = result_ && PrimaryExpr_2_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // BreakLabel?
  private static boolean PrimaryExpr_2_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrimaryExpr_2_1")) return false;
    BreakLabel(builder_, level_ + 1);
    return true;
  }

  // Expr?
  private static boolean PrimaryExpr_2_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrimaryExpr_2_2")) return false;
    Expr(builder_, level_ + 1);
    return true;
  }

  // KEYWORD_COMPTIME Expr
  private static boolean PrimaryExpr_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrimaryExpr_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_COMPTIME);
    result_ = result_ && Expr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // KEYWORD_NOSUSPEND Expr
  private static boolean PrimaryExpr_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrimaryExpr_4")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_NOSUSPEND);
    result_ = result_ && Expr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // KEYWORD_CONTINUE BreakLabel? Expr?
  private static boolean PrimaryExpr_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrimaryExpr_5")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_CONTINUE);
    result_ = result_ && PrimaryExpr_5_1(builder_, level_ + 1);
    result_ = result_ && PrimaryExpr_5_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // BreakLabel?
  private static boolean PrimaryExpr_5_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrimaryExpr_5_1")) return false;
    BreakLabel(builder_, level_ + 1);
    return true;
  }

  // Expr?
  private static boolean PrimaryExpr_5_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrimaryExpr_5_2")) return false;
    Expr(builder_, level_ + 1);
    return true;
  }

  // KEYWORD_RESUME Expr
  private static boolean PrimaryExpr_6(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrimaryExpr_6")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_RESUME);
    result_ = result_ && Expr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // KEYWORD_RETURN Expr?
  private static boolean PrimaryExpr_7(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrimaryExpr_7")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_RETURN);
    result_ = result_ && PrimaryExpr_7_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // Expr?
  private static boolean PrimaryExpr_7_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrimaryExpr_7_1")) return false;
    Expr(builder_, level_ + 1);
    return true;
  }

  // BlockLabel? LoopExpr
  private static boolean PrimaryExpr_8(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrimaryExpr_8")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = PrimaryExpr_8_0(builder_, level_ + 1);
    result_ = result_ && LoopExpr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // BlockLabel?
  private static boolean PrimaryExpr_8_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrimaryExpr_8_0")) return false;
    BlockLabel(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // BUILTINIDENTIFIER FnCallArguments
  //   | CHAR_LITERAL
  //   | ContainerDecl
  //   | DOT IDENTIFIER
  //   | DOT InitList
  //   | ErrorSetDecl
  //   | FLOAT
  //   | FnProto
  //   | GroupedExpr
  //   | LabeledTypeExpr
  //   | IDENTIFIER
  //   | IfTypeExpr
  //   | INTEGER
  //   | KEYWORD_COMPTIME TypeExpr
  //   | KEYWORD_ERROR DOT IDENTIFIER
  //   | KEYWORD_ANYFRAME
  //   | KEYWORD_UNREACHABLE
  //   | StringLiteral
  public static boolean PrimaryTypeExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrimaryTypeExpr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, PRIMARY_TYPE_EXPR, "<primary type expr>");
    result_ = PrimaryTypeExpr_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, CHAR_LITERAL);
    if (!result_) result_ = ContainerDecl(builder_, level_ + 1);
    if (!result_) result_ = parseTokens(builder_, 0, DOT, IDENTIFIER);
    if (!result_) result_ = PrimaryTypeExpr_4(builder_, level_ + 1);
    if (!result_) result_ = ErrorSetDecl(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, FLOAT);
    if (!result_) result_ = FnProto(builder_, level_ + 1);
    if (!result_) result_ = GroupedExpr(builder_, level_ + 1);
    if (!result_) result_ = LabeledTypeExpr(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, IDENTIFIER);
    if (!result_) result_ = IfTypeExpr(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, INTEGER);
    if (!result_) result_ = PrimaryTypeExpr_13(builder_, level_ + 1);
    if (!result_) result_ = parseTokens(builder_, 0, KEYWORD_ERROR, DOT, IDENTIFIER);
    if (!result_) result_ = consumeToken(builder_, KEYWORD_ANYFRAME);
    if (!result_) result_ = consumeToken(builder_, KEYWORD_UNREACHABLE);
    if (!result_) result_ = StringLiteral(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // BUILTINIDENTIFIER FnCallArguments
  private static boolean PrimaryTypeExpr_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrimaryTypeExpr_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, BUILTINIDENTIFIER);
    result_ = result_ && FnCallArguments(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // DOT InitList
  private static boolean PrimaryTypeExpr_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrimaryTypeExpr_4")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOT);
    result_ = result_ && InitList(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // KEYWORD_COMPTIME TypeExpr
  private static boolean PrimaryTypeExpr_13(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PrimaryTypeExpr_13")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_COMPTIME);
    result_ = result_ && TypeExpr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // PIPE (ASTERISK? IDENTIFIER COMMA)* (ASTERISK? IDENTIFIER) PIPE
  public static boolean PtrIndexPayload(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PtrIndexPayload")) return false;
    if (!nextTokenIs(builder_, PIPE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, PIPE);
    result_ = result_ && PtrIndexPayload_1(builder_, level_ + 1);
    result_ = result_ && PtrIndexPayload_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, PIPE);
    exit_section_(builder_, marker_, PTR_INDEX_PAYLOAD, result_);
    return result_;
  }

  // (ASTERISK? IDENTIFIER COMMA)*
  private static boolean PtrIndexPayload_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PtrIndexPayload_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!PtrIndexPayload_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "PtrIndexPayload_1", pos_)) break;
    }
    return true;
  }

  // ASTERISK? IDENTIFIER COMMA
  private static boolean PtrIndexPayload_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PtrIndexPayload_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = PtrIndexPayload_1_0_0(builder_, level_ + 1);
    result_ = result_ && consumeTokens(builder_, 0, IDENTIFIER, COMMA);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ASTERISK?
  private static boolean PtrIndexPayload_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PtrIndexPayload_1_0_0")) return false;
    consumeToken(builder_, ASTERISK);
    return true;
  }

  // ASTERISK? IDENTIFIER
  private static boolean PtrIndexPayload_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PtrIndexPayload_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = PtrIndexPayload_2_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, IDENTIFIER);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ASTERISK?
  private static boolean PtrIndexPayload_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PtrIndexPayload_2_0")) return false;
    consumeToken(builder_, ASTERISK);
    return true;
  }

  /* ********************************************************** */
  // PIPE ASTERISK? IDENTIFIER PIPE
  public static boolean PtrPayload(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PtrPayload")) return false;
    if (!nextTokenIs(builder_, PIPE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, PIPE);
    result_ = result_ && PtrPayload_1(builder_, level_ + 1);
    result_ = result_ && consumeTokens(builder_, 0, IDENTIFIER, PIPE);
    exit_section_(builder_, marker_, PTR_PAYLOAD, result_);
    return result_;
  }

  // ASTERISK?
  private static boolean PtrPayload_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PtrPayload_1")) return false;
    consumeToken(builder_, ASTERISK);
    return true;
  }

  /* ********************************************************** */
  // ASTERISK
  //   | ASTERISK2
  //   | LBRACKET ASTERISK ("c" | COLON Expr)? RBRACKET
  public static boolean PtrTypeStart(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PtrTypeStart")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PTR_TYPE_START, "<ptr type start>");
    result_ = consumeToken(builder_, ASTERISK);
    if (!result_) result_ = consumeToken(builder_, ASTERISK2);
    if (!result_) result_ = PtrTypeStart_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // LBRACKET ASTERISK ("c" | COLON Expr)? RBRACKET
  private static boolean PtrTypeStart_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PtrTypeStart_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, LBRACKET, ASTERISK);
    result_ = result_ && PtrTypeStart_2_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACKET);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ("c" | COLON Expr)?
  private static boolean PtrTypeStart_2_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PtrTypeStart_2_2")) return false;
    PtrTypeStart_2_2_0(builder_, level_ + 1);
    return true;
  }

  // "c" | COLON Expr
  private static boolean PtrTypeStart_2_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PtrTypeStart_2_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, "c");
    if (!result_) result_ = PtrTypeStart_2_2_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // COLON Expr
  private static boolean PtrTypeStart_2_2_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "PtrTypeStart_2_2_0_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && Expr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // CONTAINER_DOC_COMMENT? ContainerMembers?
  static boolean Root(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "Root")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = Root_0(builder_, level_ + 1);
    result_ = result_ && Root_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // CONTAINER_DOC_COMMENT?
  private static boolean Root_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "Root_0")) return false;
    consumeToken(builder_, CONTAINER_DOC_COMMENT);
    return true;
  }

  // ContainerMembers?
  private static boolean Root_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "Root_1")) return false;
    ContainerMembers(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // Expr (AssignOp Expr)?
  public static boolean SingleAssignExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SingleAssignExpr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, SINGLE_ASSIGN_EXPR, "<single assign expr>");
    result_ = Expr(builder_, level_ + 1);
    result_ = result_ && SingleAssignExpr_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (AssignOp Expr)?
  private static boolean SingleAssignExpr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SingleAssignExpr_1")) return false;
    SingleAssignExpr_1_0(builder_, level_ + 1);
    return true;
  }

  // AssignOp Expr
  private static boolean SingleAssignExpr_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SingleAssignExpr_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = AssignOp(builder_, level_ + 1);
    result_ = result_ && Expr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // LBRACKET (COLON Expr)? RBRACKET
  public static boolean SliceTypeStart(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SliceTypeStart")) return false;
    if (!nextTokenIs(builder_, LBRACKET)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LBRACKET);
    result_ = result_ && SliceTypeStart_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACKET);
    exit_section_(builder_, marker_, SLICE_TYPE_START, result_);
    return result_;
  }

  // (COLON Expr)?
  private static boolean SliceTypeStart_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SliceTypeStart_1")) return false;
    SliceTypeStart_1_0(builder_, level_ + 1);
    return true;
  }

  // COLON Expr
  private static boolean SliceTypeStart_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SliceTypeStart_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && Expr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // KEYWORD_COMPTIME ComptimeStatement
  //   | KEYWORD_NOSUSPEND BlockExprStatement
  //   | KEYWORD_SUSPEND BlockExprStatement
  //   | KEYWORD_DEFER BlockExprStatement
  //   | KEYWORD_ERRDEFER Payload? BlockExprStatement
  //   | IfStatement
  //   | LabeledStatement
  //   | VarDeclExprStatement
  public static boolean Statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "Statement")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, STATEMENT, "<statement>");
    result_ = Statement_0(builder_, level_ + 1);
    if (!result_) result_ = Statement_1(builder_, level_ + 1);
    if (!result_) result_ = Statement_2(builder_, level_ + 1);
    if (!result_) result_ = Statement_3(builder_, level_ + 1);
    if (!result_) result_ = Statement_4(builder_, level_ + 1);
    if (!result_) result_ = IfStatement(builder_, level_ + 1);
    if (!result_) result_ = LabeledStatement(builder_, level_ + 1);
    if (!result_) result_ = VarDeclExprStatement(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // KEYWORD_COMPTIME ComptimeStatement
  private static boolean Statement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "Statement_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_COMPTIME);
    result_ = result_ && ComptimeStatement(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // KEYWORD_NOSUSPEND BlockExprStatement
  private static boolean Statement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "Statement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_NOSUSPEND);
    result_ = result_ && BlockExprStatement(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // KEYWORD_SUSPEND BlockExprStatement
  private static boolean Statement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "Statement_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_SUSPEND);
    result_ = result_ && BlockExprStatement(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // KEYWORD_DEFER BlockExprStatement
  private static boolean Statement_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "Statement_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_DEFER);
    result_ = result_ && BlockExprStatement(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // KEYWORD_ERRDEFER Payload? BlockExprStatement
  private static boolean Statement_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "Statement_4")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_ERRDEFER);
    result_ = result_ && Statement_4_1(builder_, level_ + 1);
    result_ = result_ && BlockExprStatement(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // Payload?
  private static boolean Statement_4_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "Statement_4_1")) return false;
    Payload(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // (StringLiteral COMMA)* StringLiteral?
  public static boolean StringList(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "StringList")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, STRING_LIST, "<string list>");
    result_ = StringList_0(builder_, level_ + 1);
    result_ = result_ && StringList_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (StringLiteral COMMA)*
  private static boolean StringList_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "StringList_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!StringList_0_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "StringList_0", pos_)) break;
    }
    return true;
  }

  // StringLiteral COMMA
  private static boolean StringList_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "StringList_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = StringLiteral(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COMMA);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // StringLiteral?
  private static boolean StringList_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "StringList_1")) return false;
    StringLiteral(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // STRING_LITERAL_SINGLE | STRING_LITERAL_MULTI
  public static boolean StringLiteral(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "StringLiteral")) return false;
    if (!nextTokenIs(builder_, "<string literal>", STRING_LITERAL_MULTI, STRING_LITERAL_SINGLE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, STRING_LITERAL, "<string literal>");
    result_ = consumeToken(builder_, STRING_LITERAL_SINGLE);
    if (!result_) result_ = consumeToken(builder_, STRING_LITERAL_MULTI);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // KEYWORD_ASYNC PrimaryTypeExpr SuffixOp* FnCallArguments
  //   | PrimaryTypeExpr (SuffixOp | FnCallArguments)*
  public static boolean SuffixExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SuffixExpr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, SUFFIX_EXPR, "<suffix expr>");
    result_ = SuffixExpr_0(builder_, level_ + 1);
    if (!result_) result_ = SuffixExpr_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // KEYWORD_ASYNC PrimaryTypeExpr SuffixOp* FnCallArguments
  private static boolean SuffixExpr_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SuffixExpr_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_ASYNC);
    result_ = result_ && PrimaryTypeExpr(builder_, level_ + 1);
    result_ = result_ && SuffixExpr_0_2(builder_, level_ + 1);
    result_ = result_ && FnCallArguments(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // SuffixOp*
  private static boolean SuffixExpr_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SuffixExpr_0_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!SuffixOp(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "SuffixExpr_0_2", pos_)) break;
    }
    return true;
  }

  // PrimaryTypeExpr (SuffixOp | FnCallArguments)*
  private static boolean SuffixExpr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SuffixExpr_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = PrimaryTypeExpr(builder_, level_ + 1);
    result_ = result_ && SuffixExpr_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (SuffixOp | FnCallArguments)*
  private static boolean SuffixExpr_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SuffixExpr_1_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!SuffixExpr_1_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "SuffixExpr_1_1", pos_)) break;
    }
    return true;
  }

  // SuffixOp | FnCallArguments
  private static boolean SuffixExpr_1_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SuffixExpr_1_1_0")) return false;
    boolean result_;
    result_ = SuffixOp(builder_, level_ + 1);
    if (!result_) result_ = FnCallArguments(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // LBRACKET Expr (DOT2 (Expr? (COLON Expr)?)?)? RBRACKET
  //   | DOT IDENTIFIER
  //   | DOTASTERISK
  //   | DOTQUESTIONMARK
  public static boolean SuffixOp(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SuffixOp")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SUFFIX_OP, "<suffix op>");
    result_ = SuffixOp_0(builder_, level_ + 1);
    if (!result_) result_ = parseTokens(builder_, 0, DOT, IDENTIFIER);
    if (!result_) result_ = consumeToken(builder_, DOTASTERISK);
    if (!result_) result_ = consumeToken(builder_, DOTQUESTIONMARK);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // LBRACKET Expr (DOT2 (Expr? (COLON Expr)?)?)? RBRACKET
  private static boolean SuffixOp_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SuffixOp_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LBRACKET);
    result_ = result_ && Expr(builder_, level_ + 1);
    result_ = result_ && SuffixOp_0_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACKET);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (DOT2 (Expr? (COLON Expr)?)?)?
  private static boolean SuffixOp_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SuffixOp_0_2")) return false;
    SuffixOp_0_2_0(builder_, level_ + 1);
    return true;
  }

  // DOT2 (Expr? (COLON Expr)?)?
  private static boolean SuffixOp_0_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SuffixOp_0_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOT2);
    result_ = result_ && SuffixOp_0_2_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (Expr? (COLON Expr)?)?
  private static boolean SuffixOp_0_2_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SuffixOp_0_2_0_1")) return false;
    SuffixOp_0_2_0_1_0(builder_, level_ + 1);
    return true;
  }

  // Expr? (COLON Expr)?
  private static boolean SuffixOp_0_2_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SuffixOp_0_2_0_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = SuffixOp_0_2_0_1_0_0(builder_, level_ + 1);
    result_ = result_ && SuffixOp_0_2_0_1_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // Expr?
  private static boolean SuffixOp_0_2_0_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SuffixOp_0_2_0_1_0_0")) return false;
    Expr(builder_, level_ + 1);
    return true;
  }

  // (COLON Expr)?
  private static boolean SuffixOp_0_2_0_1_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SuffixOp_0_2_0_1_0_1")) return false;
    SuffixOp_0_2_0_1_0_1_0(builder_, level_ + 1);
    return true;
  }

  // COLON Expr
  private static boolean SuffixOp_0_2_0_1_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SuffixOp_0_2_0_1_0_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && Expr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // SwitchItem (COMMA SwitchItem)* COMMA?
  //   | KEYWORD_ELSE
  public static boolean SwitchCase(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SwitchCase")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SWITCH_CASE, "<switch case>");
    result_ = SwitchCase_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, KEYWORD_ELSE);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // SwitchItem (COMMA SwitchItem)* COMMA?
  private static boolean SwitchCase_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SwitchCase_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = SwitchItem(builder_, level_ + 1);
    result_ = result_ && SwitchCase_0_1(builder_, level_ + 1);
    result_ = result_ && SwitchCase_0_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (COMMA SwitchItem)*
  private static boolean SwitchCase_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SwitchCase_0_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!SwitchCase_0_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "SwitchCase_0_1", pos_)) break;
    }
    return true;
  }

  // COMMA SwitchItem
  private static boolean SwitchCase_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SwitchCase_0_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && SwitchItem(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // COMMA?
  private static boolean SwitchCase_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SwitchCase_0_2")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  /* ********************************************************** */
  // KEYWORD_SWITCH LPAREN Expr RPAREN LBRACE SwitchProngList RBRACE
  public static boolean SwitchExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SwitchExpr")) return false;
    if (!nextTokenIs(builder_, KEYWORD_SWITCH)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SWITCH_EXPR, null);
    result_ = consumeTokens(builder_, 1, KEYWORD_SWITCH, LPAREN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, Expr(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, consumeTokens(builder_, -1, RPAREN, LBRACE)) && result_;
    result_ = pinned_ && report_error_(builder_, SwitchProngList(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, RBRACE) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // Expr (DOT3 Expr)?
  public static boolean SwitchItem(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SwitchItem")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SWITCH_ITEM, "<switch item>");
    result_ = Expr(builder_, level_ + 1);
    result_ = result_ && SwitchItem_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (DOT3 Expr)?
  private static boolean SwitchItem_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SwitchItem_1")) return false;
    SwitchItem_1_0(builder_, level_ + 1);
    return true;
  }

  // DOT3 Expr
  private static boolean SwitchItem_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SwitchItem_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOT3);
    result_ = result_ && Expr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // KEYWORD_INLINE? SwitchCase EQUALRARROW PtrIndexPayload? SingleAssignExpr
  public static boolean SwitchProng(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SwitchProng")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SWITCH_PRONG, "<switch prong>");
    result_ = SwitchProng_0(builder_, level_ + 1);
    result_ = result_ && SwitchCase(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, EQUALRARROW);
    pinned_ = result_; // pin = 3
    result_ = result_ && report_error_(builder_, SwitchProng_3(builder_, level_ + 1));
    result_ = pinned_ && SingleAssignExpr(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // KEYWORD_INLINE?
  private static boolean SwitchProng_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SwitchProng_0")) return false;
    consumeToken(builder_, KEYWORD_INLINE);
    return true;
  }

  // PtrIndexPayload?
  private static boolean SwitchProng_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SwitchProng_3")) return false;
    PtrIndexPayload(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // (ZB_SwitchProngList_SwitchProng COMMA)* ZB_SwitchProngList_SwitchProng?
  public static boolean SwitchProngList(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SwitchProngList")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SWITCH_PRONG_LIST, "<switch prong list>");
    result_ = SwitchProngList_0(builder_, level_ + 1);
    result_ = result_ && SwitchProngList_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (ZB_SwitchProngList_SwitchProng COMMA)*
  private static boolean SwitchProngList_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SwitchProngList_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!SwitchProngList_0_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "SwitchProngList_0", pos_)) break;
    }
    return true;
  }

  // ZB_SwitchProngList_SwitchProng COMMA
  private static boolean SwitchProngList_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SwitchProngList_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ZB_SwitchProngList_SwitchProng(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COMMA);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ZB_SwitchProngList_SwitchProng?
  private static boolean SwitchProngList_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SwitchProngList_1")) return false;
    ZB_SwitchProngList_SwitchProng(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // KEYWORD_TEST (STRING_LITERAL_SINGLE | IDENTIFIER)? Block
  public static boolean TestDecl(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "TestDecl")) return false;
    if (!nextTokenIs(builder_, KEYWORD_TEST)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, TEST_DECL, null);
    result_ = consumeToken(builder_, KEYWORD_TEST);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, TestDecl_1(builder_, level_ + 1));
    result_ = pinned_ && Block(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // (STRING_LITERAL_SINGLE | IDENTIFIER)?
  private static boolean TestDecl_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "TestDecl_1")) return false;
    TestDecl_1_0(builder_, level_ + 1);
    return true;
  }

  // STRING_LITERAL_SINGLE | IDENTIFIER
  private static boolean TestDecl_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "TestDecl_1_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, STRING_LITERAL_SINGLE);
    if (!result_) result_ = consumeToken(builder_, IDENTIFIER);
    return result_;
  }

  /* ********************************************************** */
  // PrefixTypeOp* ErrorUnionExpr
  public static boolean TypeExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "TypeExpr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, TYPE_EXPR, "<type expr>");
    result_ = TypeExpr_0(builder_, level_ + 1);
    result_ = result_ && ErrorUnionExpr(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // PrefixTypeOp*
  private static boolean TypeExpr_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "TypeExpr_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!PrefixTypeOp(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "TypeExpr_0", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // VarDeclProto (COMMA (VarDeclProto | Expr))* EQUAL Expr SEMICOLON
  //   | Expr (AssignOp Expr | (COMMA (VarDeclProto | Expr))+ EQUAL Expr)? SEMICOLON
  public static boolean VarDeclExprStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "VarDeclExprStatement")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, VAR_DECL_EXPR_STATEMENT, "<var decl expr statement>");
    result_ = VarDeclExprStatement_0(builder_, level_ + 1);
    if (!result_) result_ = VarDeclExprStatement_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // VarDeclProto (COMMA (VarDeclProto | Expr))* EQUAL Expr SEMICOLON
  private static boolean VarDeclExprStatement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "VarDeclExprStatement_0")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = VarDeclProto(builder_, level_ + 1);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, VarDeclExprStatement_0_1(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, consumeToken(builder_, EQUAL)) && result_;
    result_ = pinned_ && report_error_(builder_, Expr(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, SEMICOLON) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // (COMMA (VarDeclProto | Expr))*
  private static boolean VarDeclExprStatement_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "VarDeclExprStatement_0_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!VarDeclExprStatement_0_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "VarDeclExprStatement_0_1", pos_)) break;
    }
    return true;
  }

  // COMMA (VarDeclProto | Expr)
  private static boolean VarDeclExprStatement_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "VarDeclExprStatement_0_1_0")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = consumeToken(builder_, COMMA);
    pinned_ = result_; // pin = 1
    result_ = result_ && VarDeclExprStatement_0_1_0_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // VarDeclProto | Expr
  private static boolean VarDeclExprStatement_0_1_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "VarDeclExprStatement_0_1_0_1")) return false;
    boolean result_;
    result_ = VarDeclProto(builder_, level_ + 1);
    if (!result_) result_ = Expr(builder_, level_ + 1);
    return result_;
  }

  // Expr (AssignOp Expr | (COMMA (VarDeclProto | Expr))+ EQUAL Expr)? SEMICOLON
  private static boolean VarDeclExprStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "VarDeclExprStatement_1")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = Expr(builder_, level_ + 1);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, VarDeclExprStatement_1_1(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, SEMICOLON) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // (AssignOp Expr | (COMMA (VarDeclProto | Expr))+ EQUAL Expr)?
  private static boolean VarDeclExprStatement_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "VarDeclExprStatement_1_1")) return false;
    VarDeclExprStatement_1_1_0(builder_, level_ + 1);
    return true;
  }

  // AssignOp Expr | (COMMA (VarDeclProto | Expr))+ EQUAL Expr
  private static boolean VarDeclExprStatement_1_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "VarDeclExprStatement_1_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = VarDeclExprStatement_1_1_0_0(builder_, level_ + 1);
    if (!result_) result_ = VarDeclExprStatement_1_1_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // AssignOp Expr
  private static boolean VarDeclExprStatement_1_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "VarDeclExprStatement_1_1_0_0")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = AssignOp(builder_, level_ + 1);
    pinned_ = result_; // pin = 1
    result_ = result_ && Expr(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // (COMMA (VarDeclProto | Expr))+ EQUAL Expr
  private static boolean VarDeclExprStatement_1_1_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "VarDeclExprStatement_1_1_0_1")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = VarDeclExprStatement_1_1_0_1_0(builder_, level_ + 1);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, consumeToken(builder_, EQUAL));
    result_ = pinned_ && Expr(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // (COMMA (VarDeclProto | Expr))+
  private static boolean VarDeclExprStatement_1_1_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "VarDeclExprStatement_1_1_0_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = VarDeclExprStatement_1_1_0_1_0_0(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!VarDeclExprStatement_1_1_0_1_0_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "VarDeclExprStatement_1_1_0_1_0", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // COMMA (VarDeclProto | Expr)
  private static boolean VarDeclExprStatement_1_1_0_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "VarDeclExprStatement_1_1_0_1_0_0")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = consumeToken(builder_, COMMA);
    pinned_ = result_; // pin = 1
    result_ = result_ && VarDeclExprStatement_1_1_0_1_0_0_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // VarDeclProto | Expr
  private static boolean VarDeclExprStatement_1_1_0_1_0_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "VarDeclExprStatement_1_1_0_1_0_0_1")) return false;
    boolean result_;
    result_ = VarDeclProto(builder_, level_ + 1);
    if (!result_) result_ = Expr(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // (KEYWORD_CONST | KEYWORD_VAR) IDENTIFIER (COLON TypeExpr)? ByteAlign? AddrSpace? LinkSection?
  public static boolean VarDeclProto(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "VarDeclProto")) return false;
    if (!nextTokenIs(builder_, "<var decl proto>", KEYWORD_CONST, KEYWORD_VAR)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, VAR_DECL_PROTO, "<var decl proto>");
    result_ = VarDeclProto_0(builder_, level_ + 1);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, consumeToken(builder_, IDENTIFIER));
    result_ = pinned_ && report_error_(builder_, VarDeclProto_2(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, VarDeclProto_3(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, VarDeclProto_4(builder_, level_ + 1)) && result_;
    result_ = pinned_ && VarDeclProto_5(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // KEYWORD_CONST | KEYWORD_VAR
  private static boolean VarDeclProto_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "VarDeclProto_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, KEYWORD_CONST);
    if (!result_) result_ = consumeToken(builder_, KEYWORD_VAR);
    return result_;
  }

  // (COLON TypeExpr)?
  private static boolean VarDeclProto_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "VarDeclProto_2")) return false;
    VarDeclProto_2_0(builder_, level_ + 1);
    return true;
  }

  // COLON TypeExpr
  private static boolean VarDeclProto_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "VarDeclProto_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && TypeExpr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ByteAlign?
  private static boolean VarDeclProto_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "VarDeclProto_3")) return false;
    ByteAlign(builder_, level_ + 1);
    return true;
  }

  // AddrSpace?
  private static boolean VarDeclProto_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "VarDeclProto_4")) return false;
    AddrSpace(builder_, level_ + 1);
    return true;
  }

  // LinkSection?
  private static boolean VarDeclProto_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "VarDeclProto_5")) return false;
    LinkSection(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // COLON LPAREN AssignExpr RPAREN
  public static boolean WhileContinueExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "WhileContinueExpr")) return false;
    if (!nextTokenIs(builder_, COLON)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, COLON, LPAREN);
    result_ = result_ && AssignExpr(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, WHILE_CONTINUE_EXPR, result_);
    return result_;
  }

  /* ********************************************************** */
  // WhilePrefix Expr (KEYWORD_ELSE Payload? Expr)?
  public static boolean WhileExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "WhileExpr")) return false;
    if (!nextTokenIs(builder_, KEYWORD_WHILE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = WhilePrefix(builder_, level_ + 1);
    result_ = result_ && Expr(builder_, level_ + 1);
    result_ = result_ && WhileExpr_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, WHILE_EXPR, result_);
    return result_;
  }

  // (KEYWORD_ELSE Payload? Expr)?
  private static boolean WhileExpr_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "WhileExpr_2")) return false;
    WhileExpr_2_0(builder_, level_ + 1);
    return true;
  }

  // KEYWORD_ELSE Payload? Expr
  private static boolean WhileExpr_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "WhileExpr_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_ELSE);
    result_ = result_ && WhileExpr_2_0_1(builder_, level_ + 1);
    result_ = result_ && Expr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // Payload?
  private static boolean WhileExpr_2_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "WhileExpr_2_0_1")) return false;
    Payload(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // KEYWORD_WHILE ZB_WhilePrefix_Operand PtrPayload? WhileContinueExpr?
  public static boolean WhilePrefix(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "WhilePrefix")) return false;
    if (!nextTokenIs(builder_, KEYWORD_WHILE)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, WHILE_PREFIX, null);
    result_ = consumeToken(builder_, KEYWORD_WHILE);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, ZB_WhilePrefix_Operand(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, WhilePrefix_2(builder_, level_ + 1)) && result_;
    result_ = pinned_ && WhilePrefix_3(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // PtrPayload?
  private static boolean WhilePrefix_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "WhilePrefix_2")) return false;
    PtrPayload(builder_, level_ + 1);
    return true;
  }

  // WhileContinueExpr?
  private static boolean WhilePrefix_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "WhilePrefix_3")) return false;
    WhileContinueExpr(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // WhilePrefix ZB_WhileStatement_Body
  public static boolean WhileStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "WhileStatement")) return false;
    if (!nextTokenIs(builder_, KEYWORD_WHILE)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, WHILE_STATEMENT, null);
    result_ = WhilePrefix(builder_, level_ + 1);
    pinned_ = result_; // pin = 1
    result_ = result_ && ZB_WhileStatement_Body(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // WhilePrefix TypeExpr (KEYWORD_ELSE Payload? TypeExpr)?
  public static boolean WhileTypeExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "WhileTypeExpr")) return false;
    if (!nextTokenIs(builder_, KEYWORD_WHILE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = WhilePrefix(builder_, level_ + 1);
    result_ = result_ && TypeExpr(builder_, level_ + 1);
    result_ = result_ && WhileTypeExpr_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, WHILE_TYPE_EXPR, result_);
    return result_;
  }

  // (KEYWORD_ELSE Payload? TypeExpr)?
  private static boolean WhileTypeExpr_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "WhileTypeExpr_2")) return false;
    WhileTypeExpr_2_0(builder_, level_ + 1);
    return true;
  }

  // KEYWORD_ELSE Payload? TypeExpr
  private static boolean WhileTypeExpr_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "WhileTypeExpr_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_ELSE);
    result_ = result_ && WhileTypeExpr_2_0_1(builder_, level_ + 1);
    result_ = result_ && TypeExpr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // Payload?
  private static boolean WhileTypeExpr_2_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "WhileTypeExpr_2_0_1")) return false;
    Payload(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // AssignExpr SEMICOLON
  static boolean ZB_BlockExprStatement_AssignExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_BlockExprStatement_AssignExpr")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = AssignExpr(builder_, level_ + 1);
    pinned_ = result_; // pin = 1
    result_ = result_ && consumeToken(builder_, SEMICOLON);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // Statement*
  static boolean ZB_Block_Statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_Block_Statement")) return false;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    while (true) {
      int pos_ = current_position_(builder_);
      if (!Statement(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "ZB_Block_Statement", pos_)) break;
    }
    exit_section_(builder_, level_, marker_, true, false, ZigParser::ZB_Block_Statement_recover);
    return true;
  }

  /* ********************************************************** */
  // !(RBRACE)
  static boolean ZB_Block_Statement_recover(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_Block_Statement_recover")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !consumeToken(builder_, RBRACE);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // ContainerMembers
  static boolean ZB_ContainerDeclAuto_ContainerMembers(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_ContainerDeclAuto_ContainerMembers")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = ContainerMembers(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, ZigParser::ZB_ContainerDeclAuto_ContainerMembers_recover);
    return result_;
  }

  /* ********************************************************** */
  // !(RBRACE)
  static boolean ZB_ContainerDeclAuto_ContainerMembers_recover(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_ContainerDeclAuto_ContainerMembers_recover")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !consumeToken(builder_, RBRACE);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // Expr
  static boolean ZB_ContainerDeclType_Expr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_ContainerDeclType_Expr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = Expr(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, ZigParser::ZB_ContainerDeclType_Expr_recover);
    return result_;
  }

  /* ********************************************************** */
  // !(RPAREN)
  static boolean ZB_ContainerDeclType_Expr_recover(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_ContainerDeclType_Expr_recover")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !consumeToken(builder_, RPAREN);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // Expr
  static boolean ZB_ExprList_Expr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_ExprList_Expr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = Expr(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, ZigParser::ZB_ExprList_recover);
    return result_;
  }

  /* ********************************************************** */
  // !(RPAREN | COMMA)
  static boolean ZB_ExprList_recover(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_ExprList_recover")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !ZB_ExprList_recover_0(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // RPAREN | COMMA
  private static boolean ZB_ExprList_recover_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_ExprList_recover_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, RPAREN);
    if (!result_) result_ = consumeToken(builder_, COMMA);
    return result_;
  }

  /* ********************************************************** */
  // !(COMMA | RPAREN)
  static boolean ZB_ForInput_Recover(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_ForInput_Recover")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !ZB_ForInput_Recover_0(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // COMMA | RPAREN
  private static boolean ZB_ForInput_Recover_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_ForInput_Recover_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, COMMA);
    if (!result_) result_ = consumeToken(builder_, RPAREN);
    return result_;
  }

  /* ********************************************************** */
  // ForInput (COMMA ForInput)* COMMA?
  static boolean ZB_ForParams(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_ForParams")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = ForInput(builder_, level_ + 1);
    result_ = result_ && ZB_ForParams_1(builder_, level_ + 1);
    result_ = result_ && ZB_ForParams_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, ZigParser::ZB_ForParams_Recover);
    return result_;
  }

  // (COMMA ForInput)*
  private static boolean ZB_ForParams_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_ForParams_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!ZB_ForParams_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "ZB_ForParams_1", pos_)) break;
    }
    return true;
  }

  // COMMA ForInput
  private static boolean ZB_ForParams_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_ForParams_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && ForInput(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // COMMA?
  private static boolean ZB_ForParams_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_ForParams_2")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  /* ********************************************************** */
  // !(RPAREN)
  static boolean ZB_ForParams_Recover(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_ForParams_Recover")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !consumeToken(builder_, RPAREN);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // ASTERISK? IDENTIFIER
  static boolean ZB_ForPayload_Item(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_ForPayload_Item")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = ZB_ForPayload_Item_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, IDENTIFIER);
    exit_section_(builder_, level_, marker_, result_, false, ZigParser::ZB_ForPayload_Recover);
    return result_;
  }

  // ASTERISK?
  private static boolean ZB_ForPayload_Item_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_ForPayload_Item_0")) return false;
    consumeToken(builder_, ASTERISK);
    return true;
  }

  /* ********************************************************** */
  // !(COMMA | PIPE)
  static boolean ZB_ForPayload_Recover(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_ForPayload_Recover")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !ZB_ForPayload_Recover_0(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // COMMA | PIPE
  private static boolean ZB_ForPayload_Recover_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_ForPayload_Recover_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, COMMA);
    if (!result_) result_ = consumeToken(builder_, PIPE);
    return result_;
  }

  /* ********************************************************** */
  // BlockExpr ( KEYWORD_ELSE Statement )?
  //   | AssignExpr ( SEMICOLON | KEYWORD_ELSE Statement )
  static boolean ZB_ForStatement_Body(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_ForStatement_Body")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ZB_ForStatement_Body_0(builder_, level_ + 1);
    if (!result_) result_ = ZB_ForStatement_Body_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // BlockExpr ( KEYWORD_ELSE Statement )?
  private static boolean ZB_ForStatement_Body_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_ForStatement_Body_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = BlockExpr(builder_, level_ + 1);
    result_ = result_ && ZB_ForStatement_Body_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ( KEYWORD_ELSE Statement )?
  private static boolean ZB_ForStatement_Body_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_ForStatement_Body_0_1")) return false;
    ZB_ForStatement_Body_0_1_0(builder_, level_ + 1);
    return true;
  }

  // KEYWORD_ELSE Statement
  private static boolean ZB_ForStatement_Body_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_ForStatement_Body_0_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_ELSE);
    result_ = result_ && Statement(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // AssignExpr ( SEMICOLON | KEYWORD_ELSE Statement )
  private static boolean ZB_ForStatement_Body_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_ForStatement_Body_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = AssignExpr(builder_, level_ + 1);
    result_ = result_ && ZB_ForStatement_Body_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // SEMICOLON | KEYWORD_ELSE Statement
  private static boolean ZB_ForStatement_Body_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_ForStatement_Body_1_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SEMICOLON);
    if (!result_) result_ = ZB_ForStatement_Body_1_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // KEYWORD_ELSE Statement
  private static boolean ZB_ForStatement_Body_1_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_ForStatement_Body_1_1_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_ELSE);
    result_ = result_ && Statement(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // LPAREN Expr RPAREN
  static boolean ZB_IfPrefix_Operand(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_IfPrefix_Operand")) return false;
    if (!nextTokenIs(builder_, LPAREN)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = consumeToken(builder_, LPAREN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, Expr(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, RPAREN) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // BlockExpr ( KEYWORD_ELSE Payload? Statement )?
  //   | AssignExpr ( SEMICOLON | KEYWORD_ELSE Payload? Statement )
  static boolean ZB_IfStatement_Body(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_IfStatement_Body")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ZB_IfStatement_Body_0(builder_, level_ + 1);
    if (!result_) result_ = ZB_IfStatement_Body_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // BlockExpr ( KEYWORD_ELSE Payload? Statement )?
  private static boolean ZB_IfStatement_Body_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_IfStatement_Body_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = BlockExpr(builder_, level_ + 1);
    result_ = result_ && ZB_IfStatement_Body_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ( KEYWORD_ELSE Payload? Statement )?
  private static boolean ZB_IfStatement_Body_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_IfStatement_Body_0_1")) return false;
    ZB_IfStatement_Body_0_1_0(builder_, level_ + 1);
    return true;
  }

  // KEYWORD_ELSE Payload? Statement
  private static boolean ZB_IfStatement_Body_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_IfStatement_Body_0_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_ELSE);
    result_ = result_ && ZB_IfStatement_Body_0_1_0_1(builder_, level_ + 1);
    result_ = result_ && Statement(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // Payload?
  private static boolean ZB_IfStatement_Body_0_1_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_IfStatement_Body_0_1_0_1")) return false;
    Payload(builder_, level_ + 1);
    return true;
  }

  // AssignExpr ( SEMICOLON | KEYWORD_ELSE Payload? Statement )
  private static boolean ZB_IfStatement_Body_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_IfStatement_Body_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = AssignExpr(builder_, level_ + 1);
    result_ = result_ && ZB_IfStatement_Body_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // SEMICOLON | KEYWORD_ELSE Payload? Statement
  private static boolean ZB_IfStatement_Body_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_IfStatement_Body_1_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SEMICOLON);
    if (!result_) result_ = ZB_IfStatement_Body_1_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // KEYWORD_ELSE Payload? Statement
  private static boolean ZB_IfStatement_Body_1_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_IfStatement_Body_1_1_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_ELSE);
    result_ = result_ && ZB_IfStatement_Body_1_1_1_1(builder_, level_ + 1);
    result_ = result_ && Statement(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // Payload?
  private static boolean ZB_IfStatement_Body_1_1_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_IfStatement_Body_1_1_1_1")) return false;
    Payload(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // FieldInit (COMMA ZB_InitList_FieldInit)* COMMA?
  //   | Expr (COMMA ZB_InitList_Expr)* COMMA?
  //   | ()
  static boolean ZB_InitList_Body(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_InitList_Body")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ZB_InitList_Body_0(builder_, level_ + 1);
    if (!result_) result_ = ZB_InitList_Body_1(builder_, level_ + 1);
    if (!result_) result_ = ZB_InitList_Body_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // FieldInit (COMMA ZB_InitList_FieldInit)* COMMA?
  private static boolean ZB_InitList_Body_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_InitList_Body_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = FieldInit(builder_, level_ + 1);
    result_ = result_ && ZB_InitList_Body_0_1(builder_, level_ + 1);
    result_ = result_ && ZB_InitList_Body_0_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (COMMA ZB_InitList_FieldInit)*
  private static boolean ZB_InitList_Body_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_InitList_Body_0_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!ZB_InitList_Body_0_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "ZB_InitList_Body_0_1", pos_)) break;
    }
    return true;
  }

  // COMMA ZB_InitList_FieldInit
  private static boolean ZB_InitList_Body_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_InitList_Body_0_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && ZB_InitList_FieldInit(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // COMMA?
  private static boolean ZB_InitList_Body_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_InitList_Body_0_2")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  // Expr (COMMA ZB_InitList_Expr)* COMMA?
  private static boolean ZB_InitList_Body_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_InitList_Body_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = Expr(builder_, level_ + 1);
    result_ = result_ && ZB_InitList_Body_1_1(builder_, level_ + 1);
    result_ = result_ && ZB_InitList_Body_1_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (COMMA ZB_InitList_Expr)*
  private static boolean ZB_InitList_Body_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_InitList_Body_1_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!ZB_InitList_Body_1_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "ZB_InitList_Body_1_1", pos_)) break;
    }
    return true;
  }

  // COMMA ZB_InitList_Expr
  private static boolean ZB_InitList_Body_1_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_InitList_Body_1_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && ZB_InitList_Expr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // COMMA?
  private static boolean ZB_InitList_Body_1_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_InitList_Body_1_2")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  // ()
  private static boolean ZB_InitList_Body_2(PsiBuilder builder_, int level_) {
    return true;
  }

  /* ********************************************************** */
  // Expr
  static boolean ZB_InitList_Expr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_InitList_Expr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = Expr(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, ZigParser::ZB_InitList_Recover);
    return result_;
  }

  /* ********************************************************** */
  // FieldInit
  static boolean ZB_InitList_FieldInit(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_InitList_FieldInit")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = FieldInit(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, ZigParser::ZB_InitList_Recover);
    return result_;
  }

  /* ********************************************************** */
  // !(COMMA | RBRACE)
  static boolean ZB_InitList_Recover(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_InitList_Recover")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !ZB_InitList_Recover_0(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // COMMA | RBRACE
  private static boolean ZB_InitList_Recover_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_InitList_Recover_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, COMMA);
    if (!result_) result_ = consumeToken(builder_, RBRACE);
    return result_;
  }

  /* ********************************************************** */
  // !(COMMA | RBRACE)
  static boolean ZB_SwitchProngList_Recover(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_SwitchProngList_Recover")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !ZB_SwitchProngList_Recover_0(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // COMMA | RBRACE
  private static boolean ZB_SwitchProngList_Recover_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_SwitchProngList_Recover_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, COMMA);
    if (!result_) result_ = consumeToken(builder_, RBRACE);
    return result_;
  }

  /* ********************************************************** */
  // SwitchProng
  static boolean ZB_SwitchProngList_SwitchProng(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_SwitchProngList_SwitchProng")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = SwitchProng(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, ZigParser::ZB_SwitchProngList_Recover);
    return result_;
  }

  /* ********************************************************** */
  // LPAREN Expr RPAREN
  static boolean ZB_WhilePrefix_Operand(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_WhilePrefix_Operand")) return false;
    if (!nextTokenIs(builder_, LPAREN)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = consumeToken(builder_, LPAREN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, Expr(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, RPAREN) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // BlockExpr ( KEYWORD_ELSE Payload? Statement )?
  //   | AssignExpr ( SEMICOLON | KEYWORD_ELSE Payload? Statement)
  static boolean ZB_WhileStatement_Body(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_WhileStatement_Body")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ZB_WhileStatement_Body_0(builder_, level_ + 1);
    if (!result_) result_ = ZB_WhileStatement_Body_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // BlockExpr ( KEYWORD_ELSE Payload? Statement )?
  private static boolean ZB_WhileStatement_Body_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_WhileStatement_Body_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = BlockExpr(builder_, level_ + 1);
    result_ = result_ && ZB_WhileStatement_Body_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ( KEYWORD_ELSE Payload? Statement )?
  private static boolean ZB_WhileStatement_Body_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_WhileStatement_Body_0_1")) return false;
    ZB_WhileStatement_Body_0_1_0(builder_, level_ + 1);
    return true;
  }

  // KEYWORD_ELSE Payload? Statement
  private static boolean ZB_WhileStatement_Body_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_WhileStatement_Body_0_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_ELSE);
    result_ = result_ && ZB_WhileStatement_Body_0_1_0_1(builder_, level_ + 1);
    result_ = result_ && Statement(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // Payload?
  private static boolean ZB_WhileStatement_Body_0_1_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_WhileStatement_Body_0_1_0_1")) return false;
    Payload(builder_, level_ + 1);
    return true;
  }

  // AssignExpr ( SEMICOLON | KEYWORD_ELSE Payload? Statement)
  private static boolean ZB_WhileStatement_Body_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_WhileStatement_Body_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = AssignExpr(builder_, level_ + 1);
    result_ = result_ && ZB_WhileStatement_Body_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // SEMICOLON | KEYWORD_ELSE Payload? Statement
  private static boolean ZB_WhileStatement_Body_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_WhileStatement_Body_1_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SEMICOLON);
    if (!result_) result_ = ZB_WhileStatement_Body_1_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // KEYWORD_ELSE Payload? Statement
  private static boolean ZB_WhileStatement_Body_1_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_WhileStatement_Body_1_1_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEYWORD_ELSE);
    result_ = result_ && ZB_WhileStatement_Body_1_1_1_1(builder_, level_ + 1);
    result_ = result_ && Statement(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // Payload?
  private static boolean ZB_WhileStatement_Body_1_1_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ZB_WhileStatement_Body_1_1_1_1")) return false;
    Payload(builder_, level_ + 1);
    return true;
  }

}
