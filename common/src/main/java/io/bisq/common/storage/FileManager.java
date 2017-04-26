/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.common.storage;

import com.google.common.util.concurrent.CycleDetectingLockFactory;
import io.bisq.common.UserThread;
import io.bisq.common.io.LookAheadObjectInputStream;
import io.bisq.common.persistence.Persistable;
import io.bisq.common.proto.PersistenceProtoResolver;
import io.bisq.common.util.Utilities;
import io.bisq.generated.protobuffer.PB;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class FileManager<T extends Persistable> {
    private final File dir;
    private final File storageFile;
    private final ScheduledThreadPoolExecutor executor;
    private final AtomicBoolean savePending;
    private final long delay;
    private final Callable<Void> saveFileTask;
    private T serializable;
    private final PersistenceProtoResolver persistenceProtoResolver;
    private final ReentrantLock writeLock = CycleDetectingLockFactory.newInstance(CycleDetectingLockFactory.Policies.THROW).newReentrantLock("writeLock");

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public FileManager(File dir, File storageFile, long delay, PersistenceProtoResolver persistenceProtoResolver) {
        this.dir = dir;
        this.storageFile = storageFile;
        this.persistenceProtoResolver = persistenceProtoResolver;

        executor = Utilities.getScheduledThreadPoolExecutor("FileManager", 1, 10, 5);

        // File must only be accessed from the auto-save executor from now on, to avoid simultaneous access.
        savePending = new AtomicBoolean();
        this.delay = delay;

        saveFileTask = () -> {
            Thread.currentThread().setName("Save-file-task-" + new Random().nextInt(10000));
            // Runs in an auto save thread.
            if (!savePending.getAndSet(false)) {
                // Some other scheduled request already beat us to it.
                return null;
            }
            saveNowInternal(serializable);
            return null;
        };
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            UserThread.execute(FileManager.this::shutDown);
        }, "FileManager.ShutDownHook"));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Actually write the wallet file to disk, using an atomic rename when possible. Runs on the current thread.
     */
    public void saveNow(T serializable) {
        saveNowInternal(serializable);
    }

    /**
     * Queues up a save in the background. Useful for not very important wallet changes.
     */
    public void saveLater(T serializable) {
        saveLater(serializable, delay);
    }

    public void saveLater(T serializable, long delayInMilli) {
        this.serializable = serializable;

        if (savePending.getAndSet(true))
            return;   // Already pending.

        executor.schedule(saveFileTask, delayInMilli, TimeUnit.MILLISECONDS);
    }

    public synchronized T read(File file) throws IOException, ClassNotFoundException {
        log.debug("read" + file);
        Optional<Persistable> persistable = Optional.empty();

        try (final FileInputStream fileInputStream = new FileInputStream(file)) {
            persistable = persistenceProtoResolver.fromProto(PB.DiskEnvelope.parseDelimitedFrom(fileInputStream));
        } catch (Throwable t) {
            log.warn("Exception at proto read: " + t.getMessage() + " " + file.getName());
        }

        if (persistable.isPresent()) {
            log.info("Reading Persistable: {}", persistable.get().getClass());
            //noinspection unchecked
            return (T) persistable.get();
        }

        try (final FileInputStream fileInputStream = new FileInputStream(file);
             final ObjectInputStream objectInputStream = new LookAheadObjectInputStream(fileInputStream, false)) {
            //noinspection unchecked
            log.warn("Still using Serializable storing for file: {}", file);
            return (T) objectInputStream.readObject();
        } catch (Throwable t) {
            log.error("Exception at read: " + t.getMessage());
            throw t;
        }
    }

    public synchronized void removeFile(String fileName) {
        log.debug("removeFile" + fileName);
        File file = new File(dir, fileName);
        boolean result = file.delete();
        if (!result)
            log.warn("Could not delete file: " + file.toString());

        File backupDir = new File(Paths.get(dir.getAbsolutePath(), "backup").toString());
        if (backupDir.exists()) {
            File backupFile = new File(Paths.get(dir.getAbsolutePath(), "backup", fileName).toString());
            if (backupFile.exists()) {
                result = backupFile.delete();
                if (!result)
                    log.warn("Could not delete backupFile: " + file.toString());
            }
        }
    }


    /**
     * Shut down auto-saving.
     */
    void shutDown() {
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public synchronized void removeAndBackupFile(String fileName) throws IOException {
        File corruptedBackupDir = new File(Paths.get(dir.getAbsolutePath(), "backup_of_corrupted_data").toString());
        if (!corruptedBackupDir.exists())
            if (!corruptedBackupDir.mkdir())
                log.warn("make dir failed");

        File corruptedFile = new File(Paths.get(dir.getAbsolutePath(), "backup_of_corrupted_data", fileName).toString());
        renameTempFileToFile(storageFile, corruptedFile);
    }

    public synchronized void backupFile(String fileName, int numMaxBackupFiles) {
        FileUtil.rollingBackup(dir, fileName, numMaxBackupFiles);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void saveNowInternal(T serializable) {
        long now = System.currentTimeMillis();
        saveToFile(serializable, dir, storageFile);
        log.trace("Save {} completed in {} msec", storageFile, System.currentTimeMillis() - now);
    }

    // TODO Sometimes we get a ConcurrentModificationException here
    private synchronized void saveToFile(T serializable, File dir, File storageFile) {
        File tempFile = null;
        FileOutputStream fileOutputStream = null;
        ObjectOutputStream objectOutputStream = null;
        PrintWriter printWriter = null;

        // is it a protobuffer thing?

        PB.DiskEnvelope protoDiskEnvelope = null;
        try {
            protoDiskEnvelope = (PB.DiskEnvelope) serializable.toProto();
        } catch (Throwable e) {
            log.debug("Not protobufferable: {}, {}, {}", serializable.getClass().getSimpleName(), storageFile, e.getStackTrace());
        }

        try {
            if (!dir.exists())
                if (!dir.mkdir())
                    log.warn("make dir failed");

            tempFile = File.createTempFile("temp", null, dir);
            tempFile.deleteOnExit();
            if (serializable instanceof PlainTextWrapper) {
                // When we dump json files we don't want to safe it as java serialized string objects, so we use PrintWriter instead.
                printWriter = new PrintWriter(tempFile);
                printWriter.println(((PlainTextWrapper) serializable).plainText);
            } else if (protoDiskEnvelope != null) {
                fileOutputStream = new FileOutputStream(tempFile);

                log.info("Writing protobuffer to disc:{}", serializable.getClass());
                writeLock.lock();
                protoDiskEnvelope.writeDelimitedTo(fileOutputStream);

                // Attempt to force the bits to hit the disk. In reality the OS or hard disk itself may still decide
                // to not write through to physical media for at least a few seconds, but this is the best we can do.
                fileOutputStream.flush();
                fileOutputStream.getFD().sync();
                writeLock.unlock();

                // Close resources before replacing file with temp file because otherwise it causes problems on windows
                // when rename temp file
                fileOutputStream.close();
            } else {
                log.warn("serializable write: {}", serializable.getClass());
                // Don't use auto closeable resources in try() as we would need too many try/catch clauses (for tempFile)
                // and we need to close it
                // manually before replacing file with temp file
                fileOutputStream = new FileOutputStream(tempFile);
                objectOutputStream = new ObjectOutputStream(fileOutputStream);

                writeLock.lock();
                objectOutputStream.writeObject(serializable);
                // Attempt to force the bits to hit the disk. In reality the OS or hard disk itself may still decide
                // to not write through to physical media for at least a few seconds, but this is the best we can do.
                fileOutputStream.flush();
                fileOutputStream.getFD().sync();
                writeLock.unlock();

                // Close resources before replacing file with temp file because otherwise it causes problems on windows
                // when rename temp file
                fileOutputStream.close();
                objectOutputStream.close();
            }
            renameTempFileToFile(tempFile, storageFile);
        } catch (Throwable t) {
            log.error("storageFile " + storageFile.toString());
            t.printStackTrace();
            log.error("Error at saveToFile: " + t.getMessage());
        } finally {
            if (writeLock.isLocked())
                writeLock.unlock();
            if (tempFile != null && tempFile.exists()) {
                log.warn("Temp file still exists after failed save. We will delete it now. storageFile=" + storageFile);
                if (!tempFile.delete())
                    log.error("Cannot delete temp file.");
            }

            try {
                if (objectOutputStream != null)
                    objectOutputStream.close();
                if (fileOutputStream != null)
                    fileOutputStream.close();
                if (printWriter != null)
                    printWriter.close();
            } catch (IOException e) {
                // We swallow that
                e.printStackTrace();
                log.error("Cannot close resources." + e.getMessage());
            }
        }
    }

    private synchronized void renameTempFileToFile(File tempFile, File file) throws IOException {
        if (Utilities.isWindows()) {
            // Work around an issue on Windows whereby you can't rename over existing files.
            final File canonical = file.getCanonicalFile();
            if (canonical.exists() && !canonical.delete()) {
                throw new IOException("Failed to delete canonical file for replacement with save");
            }
            if (!tempFile.renameTo(canonical)) {
                throw new IOException("Failed to rename " + tempFile + " to " + canonical);
            }
        } else if (!tempFile.renameTo(file)) {
            throw new IOException("Failed to rename " + tempFile + " to " + file);
        }
    }
}
