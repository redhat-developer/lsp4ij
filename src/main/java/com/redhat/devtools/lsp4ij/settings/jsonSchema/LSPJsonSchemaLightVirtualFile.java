// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.redhat.devtools.lsp4ij.settings.jsonSchema;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.CharsetUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFileBase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.Charset;

/**
 * In-memory implementation of {@link VirtualFile}.
 *
 * <p>We cannot use {@link com.intellij.testFramework.LightVirtualFile}
 * because IJ 2024.x? doesn't evict the Json Schema cache when {@link com.intellij.testFramework.LightVirtualFile} is used
 * because it skips the modificationStamp (when file is updated).
 *
 * See <a href="https://github.com/JetBrains/intellij-community/blob/f873c09975f3262558093855d04474598356d2a5/json/src/com/jetbrains/jsonSchema/impl/light/nodes/JsonSchemaObjectStorage.kt#L74">JsonSchemaObjectStorage</a>
 *
 * </p>
 *
 * <p>
 *     This class is a copy/paste of <a href="https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/testFramework/LightVirtualFile.java">LightVirtualFile.java</a>
 * </p>
 */
class LSPJsonSchemaLightVirtualFile extends LightVirtualFileBase {
  private CharSequence myContent;
  private Language myLanguage;
  private long myCachedLength = Long.MIN_VALUE;

  public LSPJsonSchemaLightVirtualFile(@NlsSafe @NotNull String name, @NotNull CharSequence content) {
    this(name, null, content, LocalTimeCounter.currentTime());
  }

  public LSPJsonSchemaLightVirtualFile(@NlsSafe @NotNull String name, @Nullable FileType fileType, @NotNull CharSequence text, long modificationStamp) {
    this(name, fileType, text, CharsetUtil.extractCharsetFromFileContent(null, null, fileType, text), modificationStamp);
  }

  public LSPJsonSchemaLightVirtualFile(@NlsSafe @NotNull String name,
                                       @Nullable FileType fileType,
                                       @NlsSafe @NotNull CharSequence text,
                                       Charset charset,
                                       long modificationStamp) {
    super(name, fileType, modificationStamp);
    setContentImpl(text);
    setCharset(charset);
  }

  @Override
  protected void storeCharset(Charset charset) {
    super.storeCharset(charset);
    myCachedLength = Long.MIN_VALUE;
  }

  public Language getLanguage() {
    return myLanguage;
  }

  public void setLanguage(@NotNull Language language) {
    myLanguage = language;
    FileType type = language.getAssociatedFileType();
    if (type == null) {
      type = FileTypeRegistry.getInstance().getFileTypeByFileName(getNameSequence());
    }
    setFileType(type);
  }

  @Override
  public @NotNull InputStream getInputStream() throws IOException {
    return VfsUtilCore.byteStreamSkippingBOM(doGetContent(), this);
  }

  @Override
  public long getLength() {
    long cachedLength = myCachedLength;
    if (cachedLength == Long.MIN_VALUE) {
      myCachedLength = cachedLength = super.getLength();
    }
    return cachedLength;
  }

  @Override
  public @NotNull OutputStream getOutputStream(Object requestor, final long newModificationStamp, long newTimeStamp) throws IOException {
    return VfsUtilCore.outputStreamAddingBOM(new ByteArrayOutputStream() {
      @Override
      public void close() {
        assert isWritable();

        setModificationStamp(newModificationStamp);
        try {
          setContentImpl(toString(getCharset().name()));
        }
        catch (UnsupportedEncodingException e) {
          throw new RuntimeException(e);
        }
      }
    }, this);
  }

  @Override
  public byte @NotNull [] contentsToByteArray() throws IOException {
    //long cachedLength = myCachedLength;
    //if (FileSizeLimit.isTooLarge(cachedLength, FileUtilRt.getExtension(getNameSequence()).toString())) {
    //  throw new FileTooBigException("file too big, length = "+cachedLength);
    //}
    return doGetContent();
  }

  private byte @NotNull [] doGetContent() {
    Charset charset = getCharset();
    String s = getContent().toString();
    byte[] result = s.getBytes(charset);
    byte[] bom = getBOM();
    return bom == null ? result : ArrayUtil.mergeArrays(bom, result);
  }

  public void setContent(@NotNull CharSequence content) {
    setContentImpl(content);
    setModificationStamp(LocalTimeCounter.currentTime());
  }

  private void setContentImpl(@NotNull CharSequence content) {
    myContent = content;
    myCachedLength = Long.MIN_VALUE;
  }

  public @NotNull CharSequence getContent() {
    return myContent;
  }

  @Override
  public String toString() {
    return "MyLightVirtualFile: " + getPresentableUrl();
  }

}
