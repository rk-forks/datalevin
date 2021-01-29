package datalevin.ni;

import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumLookup;
import org.graalvm.nativeimage.c.constant.CEnumValue;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Import LMDB C API and manage native memory
 */
@CContext(Lib.Directives.class)
public final class Lib {

    /**
     * Sets up the context required for interacting with native library.
     */
    public static final class Directives implements CContext.Directives {
        @Override
        public List<String> getHeaderFiles() {
            return Collections.singletonList("<lmdb.h>");
        }

        @Override
        public List<String> getLibraries() {
            return Arrays.asList("lmdb");
        }
    }

    /**
     * LMDB environment
     */
    @CStruct(value = "MDB_env", isIncomplete = true)
    public interface MDB_env extends PointerBase {}

    @CPointerTo(MDB_env.class)
    public interface MDB_envPointer extends PointerBase {
        MDB_env read();
        void write(MDB_env value);
    }

    public static final MDB_envPointer allocateEnvPtr() {
        MDB_envPointer envPtr = UnmanagedMemory.malloc(8);
        return envPtr;
    }

    public static final void freeEnvPtr(MDB_envPointer envPtr) {
        UnmanagedMemory.free(envPtr);
    }

    /**
     * LMDB transaction
     */
    @CStruct(value = "MDB_txn", isIncomplete = true)
    public interface MDB_txn extends PointerBase {}

    @CPointerTo(MDB_txn.class)
    public interface MDB_txnPointer extends PointerBase {
        MDB_txn read();
        void write(MDB_txn value);
    }

    public static final MDB_txnPointer allocateTxnPtr() {
        MDB_txnPointer txnPtr = UnmanagedMemory.malloc(8);
        return txnPtr;
    }

    public static final void freeTxnPtr(MDB_txnPointer txnPtr) {
        UnmanagedMemory.free(txnPtr);
    }

    /**
     * LMDB dbi
     */
    public interface MDB_dbiPointer extends CIntPointer {}

    public static final MDB_dbiPointer allocateDbiPtr() {
        MDB_dbiPointer dbiPtr = UnmanagedMemory.malloc(8);
        return dbiPtr;
    }

    public static final void freeDbiPtr(MDB_dbiPointer dbiPtr) {
        UnmanagedMemory.free(dbiPtr);
    }

    /**
     * LMDB cursor
     */
    @CStruct(value = "MDB_cursor", isIncomplete = true)
    public interface MDB_cursor extends PointerBase {}

    @CPointerTo(MDB_cursor.class)
    public interface MDB_cursorPointer extends PointerBase {
        MDB_cursor read();
        void write(MDB_cursor value);
    }

    public static final MDB_cursorPointer allocateCursorPtr() {
        MDB_cursorPointer cursorPtr = UnmanagedMemory.malloc(8);
        return cursorPtr;
    }

    public static final void freeCursorPtr(MDB_cursorPointer cursorPtr) {
        UnmanagedMemory.free(cursorPtr);
    }

    /**
     * Generic structure used for passing keys and data in and out
     * of the database.
     */
    @CStruct("MDB_val")
    public interface MDB_val extends PointerBase {

        @CField("mv_size")
        long get_mv_size();

        @CField("mv_size")
        void set_mv_size(long value);

        @CField("mv_data")
        VoidPointer get_mv_data();

        @CField("mv_data")
        void set_mv_data(VoidPointer value);
    }

    /**
     * A callback function used to compare two keys in a database,
     * used by <code>mdb_set_compare</code>.
     */
    public interface MDB_cmp_func extends CFunctionPointer {

        @InvokeCFunctionPointer
        int invoke(IsolateThread thread, MDB_val a, MDB_val b);
    }

    /**
     * mdb_env Environment flags
     */
    @CConstant("MDB_FIXEDMAP") public static native int MDB_FIXEDMAP();
    @CConstant("MDB_NOSUBDIR") public static native int MDB_NOSUBDIR();
    @CConstant("MDB_NOSYNC") public static native int MDB_NOSYNC();
    @CConstant("MDB_RDONLY") public static native int MDB_RDONLY();
    @CConstant("MDB_NOMETASYNC") public static native int MDB_NOMETASYNC();
    @CConstant("MDB_WRITEMAP") public static native int MDB_WRITEMAP();
    @CConstant("MDB_MAPASYNC") public static native int MDB_MAPASYNC();
    @CConstant("MDB_NOTLS") public static native int MDB_NOTLS();
    @CConstant("MDB_NOLOCK") public static native int MDB_NOLOCK();
    @CConstant("MDB_NORDAHEAD") public static native int MDB_NORDAHEAD();
    @CConstant("MDB_NOMEMINIT") public static native int MDB_NOMEMINIT();

    /**
     * mdb_dbi_open Database flags
     */
    @CConstant("MDB_REVERSEKEY") public static native int MDB_REVERSEKEY();
    @CConstant("MDB_DUPSORT") public static native int MDB_DUPSORT();
    @CConstant("MDB_INTEGERKEY") public static native int MDB_INTEGERKEY();
    @CConstant("MDB_DUPFIXED") public static native int MDB_DUPFIXED();
    @CConstant("MDB_INTEGERDUP") public static native int MDB_INTEGERDUP();
    @CConstant("MDB_REVERSEDUP") public static native int MDB_REVERSEDUP();
    @CConstant("MDB_CREATE") public static native int MDB_CREATE();

    /**
     * mdb_put Write flags
     */
    @CConstant("MDB_NOOVERWRITE") public static native int MDB_NOOVERWRITE();
    @CConstant("MDB_NODUPDATA") public static native int MDB_NODUPDATA();
    @CConstant("MDB_CURRENT") public static native int MDB_CURRENT();
    @CConstant("MDB_RESERVE") public static native int MDB_RESERVE();
    @CConstant("MDB_APPEND") public static native int MDB_APPEND();
    @CConstant("MDB_APPENDDUP") public static native int MDB_APPENDDUP();
    @CConstant("MDB_MULTIPLE") public static native int MDB_MULTIPLE();

    /**
     * mdb_copy Copy flags
     */
    @CConstant("MDB_CP_COMPACT") public static native int MDB_CP_COMPACT();

    /**
     * Cursor Get operations.
     */
    @CEnum("MDB_cursor_op")
    public enum MDB_cursor_op {
        MDB_FIRST,				/**<  Position at first key/data item */
        MDB_FIRST_DUP,		/**< Position at first data item of current key.
                             Only for #MDB_DUPSORT */
        MDB_GET_BOTH,			/**< Position at key/data pair. Only for #MDB_DUPSORT */
        MDB_GET_BOTH_RANGE,		/**< position at key, nearest data.
                                 Only for #MDB_DUPSORT */
        MDB_GET_CURRENT,		/**< Return key/data at current cursor position */
        MDB_GET_MULTIPLE,		/**< Return up to a page of duplicate data items
                               from current cursor position. Move cursor to prepare
                               for #MDB_NEXT_MULTIPLE. Only for #MDB_DUPFIXED */
        MDB_LAST,				/**< Position at last key/data item */
        MDB_LAST_DUP,			/**< Position at last data item of current key.
                             Only for #MDB_DUPSORT */
        MDB_NEXT,				/**< Position at next data item */
        MDB_NEXT_DUP,			/**< Position at next data item of current key.
                             Only for #MDB_DUPSORT */
        MDB_NEXT_MULTIPLE,		/**< Return up to a page of duplicate data items
                                 from next cursor position. Move cursor to prepare
                                 for #MDB_NEXT_MULTIPLE. Only for #MDB_DUPFIXED */
        MDB_NEXT_NODUP,			/**< Position at first data item of next key */
        MDB_PREV,				/**< Position at previous data item */
        MDB_PREV_DUP,			/**< Position at previous data item of current key.
                             Only for #MDB_DUPSORT */
        MDB_PREV_NODUP,			/**< Position at last data item of previous key */
        MDB_SET,				/**< Position at specified key */
        MDB_SET_KEY,			/**< Position at specified key, return key + data */
        MDB_SET_RANGE,			/**< Position at first key greater than or equal
                               to specified key. */
        MDB_PREV_MULTIPLE;		/**< Position at previous page and return up to
                               a page of duplicate data items.
                               Only for #MDB_DUPFIXED */

        @CEnumValue
        public native int getCValue();

        @CEnumLookup
        public static native MDB_cursor_op fromCValue(int value);
    }

    /**
     * Return codes.
     */
    @CConstant("MDB_SUCCESS") public static native int MDB_SUCCESS();
    @CConstant("MDB_KEYEXIST") public static native int MDB_KEYEXIST();
    @CConstant("MDB_NOTFOUND") public static native int MDB_NOTFOUND();
    @CConstant("MDB_PAGE_NOTFOUND")
    public static native int MDB_PAGE_NOTFOUND();
    @CConstant("MDB_CORRUPTED") public static native int MDB_CORRUPTED();
    @CConstant("MDB_PANIC") public static native int MDB_PANIC();
    @CConstant("MDB_VERSION_MISMATCH")
    public static native int MDB_VERSION_MISMATCH();
    @CConstant("MDB_INVALID") public static native int MDB_INVALID();
    @CConstant("MDB_MAP_FULL") public static native int MDB_MAP_FULL();
    @CConstant("MDB_DBS_FULL") public static native int MDB_DBS_FULL();
    @CConstant("MDB_READERS_FULL") public static native int MDB_READERS_FULL();
    @CConstant("MDB_TLS_FULL") public static native int MDB_TLS_FULL();
    @CConstant("MDB_TXN_FULL") public static native int MDB_TXN_FULL();
    @CConstant("MDB_CURSOR_FULL") public static native int MDB_CURSOR_FULL();
    @CConstant("MDB_PAGE_FULL") public static native int MDB_PAGE_FULL();
    @CConstant("MDB_MAP_RESIZED") public static native int MDB_MAP_RESIZED();
    @CConstant("MDB_INCOMPATIBLE") public static native int MDB_INCOMPATIBLE();
    @CConstant("MDB_BAD_RSLOT") public static native int MDB_BAD_RSLOT();
    @CConstant("MDB_BAD_TXN") public static native int MDB_BAD_TXN();
    @CConstant("MDB_BAD_VALSIZE") public static native int MDB_BAD_VALSIZE();
    @CConstant("MDB_BAD_DBI") public static native int MDB_BAD_DBI();

    /**
     * Statistics for a database in the environment
     */
    @CStruct("MDB_stat")
    public interface MDB_stat extends PointerBase {

        @CField("ms_psize")
        int ms_psize();

        @CField("ms_depth")
        int ms_depth();

        @CField("ms_branch_pages")
        long ms_branch_pages();

        @CField("ms_leaf_pages")
        long ms_leaf_pages();

        @CField("ms_overflow_pages")
        long ms_overflow_pages();

        @CField("ms_entries")
        long ms_entries();
    }

    public static final MDB_stat allocateStat() {
        MDB_stat stat = UnmanagedMemory.calloc(SizeOf.get(Lib.MDB_stat.class));
        return stat;
    }

    public static final void freeStat(MDB_stat stat) {
        UnmanagedMemory.free(stat);
    }

    /**
     * Information about the environment
     */
    @CStruct("MDB_envinfo")
    public interface MDB_envinfo extends PointerBase {

        @CFieldAddress("me_mapaddr")
        VoidPointer me_mapaddr();

        @CField("me_mapsize")
        long me_mapsize();

        @CField("me_last_pgno")
        long me_last_pgno();

        @CField("me_last_txnid")
        long me_last_txnid();

        @CField("me_maxreaders")
        int me_maxreaders();

        @CField("me_numreaders")
        int me_numreaders();
    }

    public static final MDB_envinfo allocateEnvinfo() {
        MDB_envinfo info =
            UnmanagedMemory.calloc(SizeOf.get(Lib.MDB_envinfo.class));
        return info;
    }

    public static final void freeEnvinfo(MDB_envinfo info) {
        UnmanagedMemory.free(info);
    }

    /**
     * General functions
     */
    @CFunction("mdb_version")
    public static native CCharPointer mdb_version(CIntPointer major,
                                                  CIntPointer minor,
                                                  CIntPointer patch);

    @CFunction("mdb_strerror")
    public static native CCharPointer mdb_strerror(int err);

    /**
     * LMDB environment functions
     */
    @CFunction("mdb_env_create")
    public static native int mdb_env_create(MDB_envPointer envPtr);

    @CFunction("mdb_env_open")
    public static native int mdb_env_open(MDB_env env, CCharPointer path,
                                          int flags, int mode);

    @CFunction("mdb_env_copy")
    public static native int mdb_env_copy(MDB_env env, CCharPointer path);

    @CFunction("mdb_env_copyfd")
    public static native int mdb_env_copyfd(MDB_env env, int fd);

    @CFunction("mdb_env_copy2")
    public static native int mdb_env_copy2(MDB_env env, CCharPointer path,
                                           int flags);

    @CFunction("mdb_env_copyfd2")
    public static native int mdb_env_copyfd2(MDB_env env, int fd, int flags);

    @CFunction("mdb_env_stat")
    public static native int mdb_env_stat(MDB_env env, MDB_stat stat);

    @CFunction("mdb_env_info")
    public static native int mdb_env_info(MDB_env env, MDB_envinfo info);

    @CFunction("mdb_env_sync")
    public static native int mdb_env_sync(MDB_env env, int force);

    @CFunction("mdb_env_close")
    public static native void mdb_env_close(MDB_env env);

    @CFunction("mdb_env_set_flags")
    public static native int mdb_env_set_flags(MDB_env env, int flags,
                                               int onoff);

    @CFunction("mdb_env_get_flags")
    public static native int mdb_env_get_flags(MDB_env env, CIntPointer flags);

    @CFunction("mdb_env_get_path")
    public static native int mdb_env_get_path(MDB_env env,
                                              CCharPointerPointer path);

    @CFunction("mdb_env_get_fd")
    public static native int mdb_env_get_fd(MDB_env env, CIntPointer fd);

    @CFunction("mdb_env_set_mapsize")
    public static native int mdb_env_set_mapsize(MDB_env env, long size);

    @CFunction("mdb_env_set_maxreaders")
    public static native int mdb_env_set_maxreaders(MDB_env env, int readers);

    @CFunction("mdb_env_get_maxreaders")
    public static native int mdb_env_get_maxreaders(MDB_env env,
                                                    CIntPointer readers);

    @CFunction("mdb_env_set_maxdbs")
    public static native int mdb_env_set_maxdbs(MDB_env env, int dbs);

    @CFunction("mdb_env_get_maxkeysize")
    public static native int mdb_env_get_maxkeysize(MDB_env env);

    @CFunction("mdb_env_set_userctx")
    public static native int mdb_env_set_userctx(MDB_env env, VoidPointer ctx);

    @CFunction("mdb_env_get_userctx")
    public static native VoidPointer mdb_env_get_userctx(MDB_env env);

    /**
     * A callback function for most LMDB assert() failures,
     * called before printing the message and aborting.
     */
    public interface MDB_assert_func extends CFunctionPointer {

        @InvokeCFunctionPointer
        int invoke(IsolateThread thread, MDB_env env, CCharPointer msg);
    }

    @CFunction("mdb_env_set_assert")
    public static native VoidPointer mdb_env_set_assert(MDB_env env,
                                                        MDB_assert_func func);

    /**
     * LMDB transaction functions
     */
    @CFunction("mdb_txn_begin")
    public static native int mdb_txn_begin(MDB_env env, MDB_txn parentTx,
                                           int flags, MDB_txnPointer txnPtr);

    @CFunction("mdb_txn_env")
    public static native Pointer mdb_txn_env(MDB_txn txn);

    @CFunction("mdb_txn_id")
    public static native long mdb_txn_id(MDB_txn txn);

    @CFunction("mdb_txn_commit")
    public static native int mdb_txn_commit(MDB_txn txn);

    @CFunction("mdb_txn_abort")
    public static native void mdb_txn_abort(MDB_txn txn);

    @CFunction("mdb_txn_reset")
    public static native void mdb_txn_reset(MDB_txn txn);

    @CFunction("mdb_txn_renew")
    public static native int mdb_txn_renew(MDB_txn txn);

    /**
     * LMDB dbi functions
     */
    @CFunction("mdb_dbi_open")
    public static native int mdb_dbi_open(MDB_txn txn, CCharPointer name,
                                          int flags, MDB_dbiPointer dbi);

    @CFunction("mdb_stat")
    public static native int mdb_stat(MDB_txn txn, int dbi, MDB_stat stat);

    @CFunction("mdb_dbi_flags")
    public static native int mdb_dbi_flags(MDB_txn txn, int dbi,
                                           CIntPointer flags);

    @CFunction("mdb_dbi_close")
    public static native void mdb_dbi_close(MDB_env env, int dbi);

    @CFunction("mdb_drop")
    public static native int mdb_drop(MDB_txn txn, int dbi, int del);

    /**
     * LMDB data access functions
     */
    @CFunction("mdb_set_compare")
    public static native int mdb_set_compare(MDB_txn txn, int dbi,
                                             MDB_cmp_func cmp);

    @CFunction("mdb_set_dupsort")
    public static native int mdb_set_dupsort(MDB_txn txn, int dbi,
                                             MDB_cmp_func cmp);

    @CFunction("mdb_get")
    public static native int mdb_get(MDB_txn txn, int dbi, MDB_val key,
                                     MDB_val data);

    @CFunction("mdb_put")
    public static native int mdb_put(MDB_txn txn, int dbi, MDB_val key,
                                     MDB_val data, int flags);

    @CFunction("mdb_del")
    public static native int mdb_del(MDB_txn txn, int dbi, MDB_val key,
                                     MDB_val data);

    /**
     * Cursor functions
     */
    @CFunction("mdb_cursor_open")
    public static native int mdb_cursor_open(MDB_txn txn, int dbi,
                                             MDB_cursorPointer cursorPtr);

    @CFunction("mdb_cursor_close")
    public static native void mdb_cursor_close(MDB_cursor cursor);

    @CFunction("mdb_cursor_renew")
    public static native int mdb_cursor_renew(MDB_txn txn, MDB_cursor cursor);

    @CFunction("mdb_cursor_txn")
    public static native MDB_txn mdb_cursor_txn(MDB_cursor cursor);

    @CFunction("mdb_cursor_dbi")
    public static native int mdb_cursor_dbi(MDB_cursor cursor);

    @CFunction("mdb_cursor_get")
    public static native int mdb_cursor_get(MDB_cursor cursor, MDB_val k,
                                            MDB_val v, MDB_cursor_op cursorOp);

    @CFunction("mdb_cursor_put")
    public static native int mdb_cursor_put(MDB_cursor cursor, MDB_val key,
                                            MDB_val data, int flags);

    @CFunction("mdb_cursor_del")
    public static native int mdb_cursor_del(MDB_cursor cursor, int flags);

    @CFunction("mdb_cursor_count")
    public static native int mdb_cursor_count(MDB_cursor cursor,
                                              CLongPointer countp);

    /**
     * Utitily functions
     */
    @CFunction("mdb_cmp")
    public static native int mdb_cmp(MDB_txn txn, int dbi, MDB_val a,
                                     MDB_val b);

    @CFunction("mdb_dcmp")
    public static native int mdb_dcmp(MDB_txn txn, int dbi, MDB_val a,
                                      MDB_val b);

    /**
     * Error checking
     */
    public static class LMDBException extends RuntimeException {
        public LMDBException(String msg) {
            super(msg);
        }
    }

    public static class BadReaderLockException extends LMDBException {
        public BadReaderLockException(String msg) {
            super(msg);
        }
    }

    public static class MapFullException extends LMDBException {
        public MapFullException(String msg) {
            super(msg);
        }
    }

    /**
     * Specialize some exceptions that we care about
     */
    public static void checkRc(int code) {
        String msg;
        if (code == MDB_SUCCESS()) {
            return;
        } else if (code == MDB_BAD_RSLOT()) {
            throw new BadReaderLockException("");
        } else if (code == MDB_MAP_FULL()) {
            throw new MapFullException("");
        } else {
            msg = CTypeConversion.toJavaString(mdb_strerror(code));
            throw new LMDBException(msg);
        }
    }

    /**
     * A callback function used to print a message from the library.
     */
    public interface MDB_msg_func extends CFunctionPointer {

        @InvokeCFunctionPointer
        int invoke(IsolateThread thread, CCharPointer msg, VoidPointer ctx);
    }

    @CFunction("mdb_reader_list")
    public static native int mdb_reader_check(MDB_env env, MDB_msg_func func,
                                              VoidPointer ctx);

    @CFunction("mdb_reader_check")
    public static native int mdb_reader_check(MDB_env env, CIntPointer dead);

}
