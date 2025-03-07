package com.digitalpetri.opcua.server.objects;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ulong;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.milo.opcua.sdk.server.AbstractLifecycle;
import org.eclipse.milo.opcua.sdk.server.Session;
import org.eclipse.milo.opcua.sdk.server.SessionListener;
import org.eclipse.milo.opcua.sdk.server.methods.MethodInvocationHandler;
import org.eclipse.milo.opcua.sdk.server.methods.Out;
import org.eclipse.milo.opcua.sdk.server.model.objects.FileType;
import org.eclipse.milo.opcua.sdk.server.model.objects.FileTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilter;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilters;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.ULong;
import org.eclipse.milo.shaded.com.google.common.collect.HashBasedTable;
import org.eclipse.milo.shaded.com.google.common.collect.Table;
import org.eclipse.milo.shaded.com.google.common.collect.Tables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation behavior for an instance of the {@link FileType} Object.
 *
 * @see <a href="https://reference.opcfoundation.org/Core/Part20/v105/docs/4.2">
 *     https://reference.opcfoundation.org/Core/Part20/v105/docs/4.2</a>
 */
public class FileObject extends AbstractLifecycle {

  /** Mask that isolates Read in the mode argument. */
  protected static final int MASK_READ = 0b0001;

  /** Mask that isolates Write in the mode argument. */
  protected static final int MASK_WRITE = 0b00010;

  /** Mask that isolates EraseExisting in the mode argument. */
  protected static final int MASK_ERASE_EXISTING = 0b0100;

  /** Mask that isolates Append in the mode argument. */
  protected static final int MASK_APPEND = 0b1000;

  protected final Logger logger = LoggerFactory.getLogger(getClass());

  protected final Table<NodeId, UInteger, FileHandle> handles =
      Tables.synchronizedTable(HashBasedTable.create());

  private volatile SessionListener sessionListener;

  private final FileTypeNode fileNode;
  private final FileSupplier fileSupplier;

  public FileObject(FileTypeNode fileNode, File file) {
    this(fileNode, () -> file);
  }

  public FileObject(FileTypeNode fileNode, FileSupplier fileSupplier) {
    this.fileNode = fileNode;
    this.fileSupplier = fileSupplier;
  }

  @Override
  protected void onStartup() {
    UaMethodNode openMethodNode = fileNode.getOpenMethodNode();
    openMethodNode.setInvocationHandler(newOpenMethod(openMethodNode));

    UaMethodNode closeMethodNode = fileNode.getCloseMethodNode();
    closeMethodNode.setInvocationHandler(newCloseMethod(closeMethodNode));

    UaMethodNode readMethodNode = fileNode.getReadMethodNode();
    readMethodNode.setInvocationHandler(newReadMethod(readMethodNode));

    UaMethodNode writeMethodNode = fileNode.getWriteMethodNode();
    writeMethodNode.setInvocationHandler(newWriteMethod(writeMethodNode));

    UaMethodNode getPositionMethodNode = fileNode.getGetPositionMethodNode();
    getPositionMethodNode.setInvocationHandler(newGetPositionMethod(getPositionMethodNode));

    UaMethodNode setPositionMethodNode = fileNode.getSetPositionMethodNode();
    setPositionMethodNode.setInvocationHandler(newSetPositionMethod(setPositionMethodNode));

    fileNode.getOpenCountNode().getFilterChain().addLast(newOpenCountAttributeFilter());
    fileNode.getSizeNode().getFilterChain().addLast(newSizeAttributeFilter());

    // TODO remove AttributeFilters on shutdown

    fileNode
        .getNodeContext()
        .getServer()
        .getSessionManager()
        .addSessionListener(
            sessionListener =
                new SessionListener() {
                  @Override
                  public void onSessionClosed(Session session) {
                    handles.row(session.getSessionId()).clear();
                  }
                });

    logger.debug("FileObject started: {}", fileNode.getNodeId());
  }

  @Override
  protected void onShutdown() {
    fileNode.getOpenMethodNode().setInvocationHandler(MethodInvocationHandler.NOT_IMPLEMENTED);
    fileNode.getCloseMethodNode().setInvocationHandler(MethodInvocationHandler.NOT_IMPLEMENTED);
    fileNode.getReadMethodNode().setInvocationHandler(MethodInvocationHandler.NOT_IMPLEMENTED);
    fileNode.getWriteMethodNode().setInvocationHandler(MethodInvocationHandler.NOT_IMPLEMENTED);
    fileNode
        .getGetPositionMethodNode()
        .setInvocationHandler(MethodInvocationHandler.NOT_IMPLEMENTED);
    fileNode
        .getSetPositionMethodNode()
        .setInvocationHandler(MethodInvocationHandler.NOT_IMPLEMENTED);

    fileNode
        .getNodeContext()
        .getServer()
        .getSessionManager()
        .removeSessionListener(sessionListener);

    logger.debug("FileObject stopped: {}", fileNode.getNodeId());
  }

  /**
   * @return {@code true} if any file handle is open.
   */
  protected boolean isOpen() {
    return !handles.isEmpty();
  }

  /**
   * @return {@code true} if any file handle is open for writing.
   */
  protected boolean isOpenForWriting() {
    return handles.values().stream()
        .anyMatch(handle -> (handle.mode.intValue() & MASK_WRITE) == MASK_WRITE);
  }

  protected FileType.OpenMethod newOpenMethod(UaMethodNode methodNode) {
    return new OpenMethodImpl(methodNode);
  }

  protected FileType.CloseMethod newCloseMethod(UaMethodNode methodNode) {
    return new CloseMethodImpl(methodNode);
  }

  protected FileType.ReadMethod newReadMethod(UaMethodNode methodNode) {
    return new ReadMethodImpl(methodNode);
  }

  protected FileType.WriteMethod newWriteMethod(UaMethodNode methodNode) {
    return new WriteMethodImpl(methodNode);
  }

  protected FileType.GetPositionMethod newGetPositionMethod(UaMethodNode methodNode) {
    return new GetPositionMethodImpl(methodNode);
  }

  protected FileType.SetPositionMethod newSetPositionMethod(UaMethodNode methodNode) {
    return new SetPositionMethodImpl(methodNode);
  }

  protected AttributeFilter newOpenCountAttributeFilter() {
    return AttributeFilters.getValue(
        ctx -> {
          var openCount = ushort(handles.size());

          return new DataValue(new Variant(openCount));
        });
  }

  protected AttributeFilter newSizeAttributeFilter() {
    return AttributeFilters.getValue(
        ctx -> {
          var length = 0L;
          try {
            length = fileSupplier.get().length();
          } catch (IOException ignored) {
          }

          var size = ulong(length);

          return new DataValue(new Variant(size));
        });
  }

  @FunctionalInterface
  public interface FileSupplier {

    /**
     * Get a {@link File} instance to be represented by this {@link FileObject}.
     *
     * <p>This method will be called each time a new file handle is opened.
     *
     * @return the {@link File} instance represented by this {@link FileObject}.
     * @throws IOException if an I/O error occurs getting the file.
     */
    File get() throws IOException;
  }

  protected static class FileHandle {

    private final AtomicLong handleSequence = new AtomicLong(0L);

    final UInteger handle = uint(handleSequence.getAndIncrement());

    final UByte mode;
    final RandomAccessFile file;

    public FileHandle(UByte mode, RandomAccessFile file) {
      this.mode = mode;
      this.file = file;
    }
  }

  /**
   * Default implementation of {@link FileType.OpenMethod}.
   *
   * <p>File operations are executed via a {@link RandomAccessFile} constructed using the {@link
   * File} instance represented by this {@link FileObject}.
   *
   * @see <a href="https://reference.opcfoundation.org/Core/Part20/v105/docs/4.2.2">
   *     https://reference.opcfoundation.org/Core/Part20/v105/docs/4.2.2</a>
   */
  public class OpenMethodImpl extends FileType.OpenMethod {

    public OpenMethodImpl(UaMethodNode node) {
      super(node);
    }

    @Override
    protected void invoke(InvocationContext context, UByte mode, Out<UInteger> fileHandle)
        throws UaException {

      Session session = context.getSession().orElseThrow();

      if (mode.intValue() == 0) {
        throw new UaException(StatusCodes.Bad_InvalidArgument, "invalid mode: " + mode);
      }

      // bits: Read, Write, EraseExisting, Append
      var modeString = "";
      var erase = false;

      if ((mode.intValue() & MASK_READ) == MASK_READ) {
        if (isOpenForWriting()) {
          throw new UaException(StatusCodes.Bad_NotReadable, "already open for writing");
        }
        modeString += "r";
      }

      if ((mode.intValue() & MASK_WRITE) == MASK_WRITE) {
        if (isOpen()) {
          throw new UaException(StatusCodes.Bad_NotWritable, "already open");
        }
        if (modeString.startsWith("r")) {
          modeString += "ws";
        } else {
          modeString += "rws";
        }
      }

      if ((mode.intValue() & MASK_ERASE_EXISTING) == MASK_ERASE_EXISTING) {
        if ((mode.intValue() & MASK_WRITE) != MASK_WRITE) {
          throw new UaException(StatusCodes.Bad_InvalidArgument, "EraseExisting requires Write");
        }
        erase = true;
      }

      try {
        File file = fileSupplier.get();

        if (erase) {
          try {
            new FileOutputStream(file).close();
          } catch (IOException e) {
            throw new UaException(StatusCodes.Bad_NotWritable, e);
          }
        }

        var handle = new FileHandle(mode, new RandomAccessFile(file, modeString));
        handles.put(session.getSessionId(), handle.handle, handle);

        fileHandle.set(handle.handle);
      } catch (IOException e) {
        throw new UaException(StatusCodes.Bad_UnexpectedError, e);
      }
    }
  }

  /**
   * Default implementation of {@link FileType.CloseMethod}.
   *
   * @see <a href="https://reference.opcfoundation.org/Core/Part20/v105/docs/4.2.3">
   *     https://reference.opcfoundation.org/Core/Part20/v105/docs/4.2.3</a>
   */
  public class CloseMethodImpl extends FileType.CloseMethod {

    public CloseMethodImpl(UaMethodNode node) {
      super(node);
    }

    @Override
    protected void invoke(InvocationContext context, UInteger fileHandle) throws UaException {
      Session session = context.getSession().orElseThrow();

      FileHandle handle = handles.remove(session.getSessionId(), fileHandle);

      if (handle == null) {
        throw new UaException(StatusCodes.Bad_NotFound);
      }

      try {
        handle.file.close();
      } catch (IOException e) {
        throw new UaException(StatusCodes.Bad_UnexpectedError, e);
      }
    }
  }

  /**
   * Default implementation of {@link FileType.ReadMethod}.
   *
   * @see <a href="https://reference.opcfoundation.org/Core/Part20/v105/docs/4.2.4">
   *     https://reference.opcfoundation.org/Core/Part20/v105/docs/4.2.4</a>
   */
  public class ReadMethodImpl extends FileType.ReadMethod {

    public ReadMethodImpl(UaMethodNode node) {
      super(node);
    }

    @Override
    protected void invoke(
        InvocationContext context, UInteger fileHandle, Integer length, Out<ByteString> data)
        throws UaException {

      Session session = context.getSession().orElseThrow();

      FileHandle handle = handles.get(session.getSessionId(), fileHandle);

      if (handle == null) {
        throw new UaException(StatusCodes.Bad_NotFound);
      }

      if ((handle.mode.intValue() & MASK_READ) != MASK_READ) {
        throw new UaException(StatusCodes.Bad_NotReadable);
      }

      try {
        byte[] bs = new byte[length];
        int read = handle.file.read(bs);

        if (read == -1) {
          data.set(ByteString.of(new byte[0]));
        } else if (read < length) {
          byte[] partial = new byte[read];
          System.arraycopy(bs, 0, partial, 0, read);
          data.set(ByteString.of(partial));
        } else {
          data.set(ByteString.of(bs));
        }
      } catch (IOException e) {
        throw new UaException(StatusCodes.Bad_UnexpectedError, e);
      }
    }
  }

  /**
   * Default implementation of {@link FileType.WriteMethod}.
   *
   * @see <a href="https://reference.opcfoundation.org/Core/Part20/v105/docs/4.2.5">
   *     https://reference.opcfoundation.org/Core/Part20/v105/docs/4.2.5</a>
   */
  public class WriteMethodImpl extends FileType.WriteMethod {

    /** Tracks if the file has been repositioned for append before the first write. */
    private final AtomicBoolean repositioned = new AtomicBoolean(false);

    public WriteMethodImpl(UaMethodNode node) {
      super(node);
    }

    @Override
    protected void invoke(InvocationContext context, UInteger fileHandle, ByteString data)
        throws UaException {

      Session session = context.getSession().orElseThrow();
      FileHandle handle = handles.get(session.getSessionId(), fileHandle);

      if (handle == null) {
        throw new UaException(StatusCodes.Bad_NotFound);
      }

      if ((handle.mode.intValue() & MASK_WRITE) != MASK_WRITE) {
        throw new UaException(StatusCodes.Bad_NotWritable);
      }

      if ((handle.mode.intValue() & MASK_APPEND) == MASK_APPEND) {
        if (repositioned.compareAndSet(false, true)) {
          try {
            handle.file.seek(handle.file.length());
          } catch (IOException e) {
            throw new UaException(StatusCodes.Bad_UnexpectedError, e);
          }
        }
      }

      try {
        handle.file.write(data.bytes());
      } catch (IOException e) {
        throw new UaException(StatusCodes.Bad_UnexpectedError, e);
      }
    }
  }

  /**
   * Default implementation of {@link FileType.GetPositionMethod}.
   *
   * @see <a href="https://reference.opcfoundation.org/Core/Part20/v105/docs/4.2.6">
   *     https://reference.opcfoundation.org/Core/Part20/v105/docs/4.2.6</a>
   */
  public class GetPositionMethodImpl extends FileType.GetPositionMethod {

    public GetPositionMethodImpl(UaMethodNode node) {
      super(node);
    }

    @Override
    protected void invoke(InvocationContext context, UInteger fileHandle, Out<ULong> position)
        throws UaException {

      Session session = context.getSession().orElseThrow();

      FileHandle handle = handles.get(session.getSessionId(), fileHandle);

      if (handle == null) {
        throw new UaException(StatusCodes.Bad_NotFound);
      }

      try {
        position.set(ulong(handle.file.getFilePointer()));
      } catch (IOException e) {
        throw new UaException(StatusCodes.Bad_UnexpectedError, e);
      }
    }
  }

  /**
   * Default implementation of {@link FileType.SetPositionMethod}.
   *
   * @see <a href="https://reference.opcfoundation.org/Core/Part20/v105/docs/4.2.7">
   *     https://reference.opcfoundation.org/Core/Part20/v105/docs/4.2.7</a>
   */
  public class SetPositionMethodImpl extends FileType.SetPositionMethod {

    public SetPositionMethodImpl(UaMethodNode node) {
      super(node);
    }

    @Override
    protected void invoke(InvocationContext context, UInteger fileHandle, ULong position)
        throws UaException {

      Session session = context.getSession().orElseThrow();

      FileHandle handle = handles.get(session.getSessionId(), fileHandle);

      if (handle == null) {
        throw new UaException(StatusCodes.Bad_NotFound);
      }

      try {
        handle.file.seek(position.longValue());
      } catch (IOException e) {
        throw new UaException(StatusCodes.Bad_UnexpectedError, e);
      }
    }
  }
}
