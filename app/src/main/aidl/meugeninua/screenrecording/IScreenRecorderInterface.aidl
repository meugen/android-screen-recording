// IScreenRecorderInterface.aidl
package meugeninua.screenrecording;

import meugeninua.screenrecording.ScreenRecorderParams;

interface IScreenRecorderInterface {

    void start(in ScreenRecorderParams params);
    void stop();
    void flush();
}