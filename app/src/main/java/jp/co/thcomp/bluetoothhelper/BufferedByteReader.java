package jp.co.thcomp.bluetoothhelper;

import android.support.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;

import jp.co.thcomp.util.LogUtil;

class BufferedByteReader extends InputStream {
    private static final HashMap<InputStream, byte[]> sStoredByteBufferMap = new HashMap<InputStream, byte[]>();

    public static void releaseStoredByteBuffer(InputStream targetStream) {
        synchronized (sStoredByteBufferMap) {
            sStoredByteBufferMap.remove(targetStream);
        }
    }

    public static void releaseAllStoredByteBuffer() {
        synchronized (sStoredByteBufferMap) {
            sStoredByteBufferMap.clear();
        }
    }

    private InputStream mInputStream;

    public BufferedByteReader(@NonNull InputStream stream) {
        if (stream == null) {
            throw new NullPointerException("stream == null");
        }

        mInputStream = stream;
    }

    @Override
    public void close() throws IOException {
        synchronized (sStoredByteBufferMap) {
            sStoredByteBufferMap.remove(mInputStream);
        }
        mInputStream.close();
    }

    @Override
    public int read() throws IOException {
        Integer ret = null;

        synchronized (sStoredByteBufferMap) {
            byte[] storedBuffer = sStoredByteBufferMap.remove(mInputStream);
            if (storedBuffer != null) {
                ret = (int) storedBuffer[0];
                if (storedBuffer.length > 1) {
                    storedBuffer = Arrays.copyOfRange(storedBuffer, 1, storedBuffer.length);
                    sStoredByteBufferMap.put(mInputStream, storedBuffer);
                }
            }
        }

        if (ret == null) {
            ret = mInputStream.read();
        }

        return ret;
    }

    public byte[] readLine(byte[] lineDelimiter) throws IOException {
        byte[] ret = null;

        synchronized (sStoredByteBufferMap) {
            byte[] storedBuffer = sStoredByteBufferMap.remove(mInputStream);

            int delimiterPosition = findDelimiter(storedBuffer, storedBuffer == null ? 0 : storedBuffer.length, lineDelimiter);
            if (delimiterPosition >= 0) {
                // delimiterを削除し、その後ろに未だデータがあれば、保持
                ret = Arrays.copyOfRange(storedBuffer, 0, delimiterPosition);

                if (delimiterPosition + lineDelimiter.length < storedBuffer.length) {
                    storedBuffer = Arrays.copyOfRange(storedBuffer, delimiterPosition + lineDelimiter.length, storedBuffer.length);
                    sStoredByteBufferMap.put(mInputStream, storedBuffer);
                }
            }

            if (ret == null) {
                ByteArrayOutputStream outStream = new ByteArrayOutputStream();

                if (storedBuffer != null) {
                    outStream.write(storedBuffer);
                }

                byte[] readBuffer = new byte[1024];
                int readSize = 0;

                while (true) {
                    if (BluetoothAccessHelper.isEnableDebug()) {
                        LogUtil.d(BluetoothAccessHelper.class.getSimpleName(), "start receive");
                    }
                    readSize = mInputStream.read(readBuffer, 0, readBuffer.length);
                    if (BluetoothAccessHelper.isEnableDebug()) {
                        LogUtil.d(BluetoothAccessHelper.class.getSimpleName(), "end receive");
                    }

                    if (readSize <= 0) {
                        if (BluetoothAccessHelper.isEnableDebug()) {
                            LogUtil.d(BluetoothAccessHelper.class.getSimpleName(), "readLine finish 1");
                        }
                        break;
                    } else {
                        outStream.write(readBuffer, 0, readSize);

                        byte[] tempBuffer = outStream.toByteArray();
                        delimiterPosition = findDelimiter(tempBuffer, tempBuffer.length, lineDelimiter);
                        if (delimiterPosition >= 0) {
                            ret = Arrays.copyOfRange(tempBuffer, 0, delimiterPosition);

                            // delimiterを削除し、その後ろに未だデータがあれば、保持
                            if (delimiterPosition + lineDelimiter.length < tempBuffer.length) {
                                storedBuffer = Arrays.copyOfRange(tempBuffer, delimiterPosition + lineDelimiter.length, tempBuffer.length);
                                sStoredByteBufferMap.put(mInputStream, storedBuffer);
                            } else {
                                sStoredByteBufferMap.remove(mInputStream);
                            }
                            if (BluetoothAccessHelper.isEnableDebug()) {
                                LogUtil.d(BluetoothAccessHelper.class.getSimpleName(), "readLine finish 2");
                            }
                            break;
                        } else {
                            sStoredByteBufferMap.put(mInputStream, tempBuffer);
                        }
                    }
                }
            }

            return ret;
        }
    }

    private int findDelimiter(byte[] data, int dataSize, byte[] delimiter) {
        int ret = -1;

        if (data != null) {
            for (int i = 0, sizeI = dataSize - delimiter.length; i <= sizeI; i++) {
                boolean find = true;

                for (int j = 0, sizeJ = delimiter.length; j < sizeJ; j++) {
                    if (data[i + j] != delimiter[j]) {
                        find = false;
                        break;
                    }
                }

                if (find) {
                    ret = i;
                    break;
                }
            }
        }

        return ret;
    }
}
