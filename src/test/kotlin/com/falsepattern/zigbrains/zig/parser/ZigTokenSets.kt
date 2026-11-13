/*
 * This file is part of ZigBrains.
 *
 * Copyright (C) 2023-2025 FalsePattern
 * All Rights Reserved
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * ZigBrains is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, only version 3 of the License.
 *
 * ZigBrains is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ZigBrains. If not, see <https://www.gnu.org/licenses/>.
 */

package com.falsepattern.zigbrains.zig.parser

import com.falsepattern.zigbrains.zig.psi.ZigTypes
import com.intellij.psi.tree.TokenSet

object ZigTokenSets {
    val COMMENTS = TokenSet.create(ZigTypes.LINE_COMMENT, ZigTypes.DOC_COMMENT, ZigTypes.CONTAINER_DOC_COMMENT)
    val STRINGS = TokenSet.create(ZigTypes.STRING_LITERAL_SINGLE, ZigTypes.STRING_LITERAL_MULTI)
}