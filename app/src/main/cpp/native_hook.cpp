#include "native_api.h"
#include "frida-gumjs.h"

#include <android/log.h>
#include <string>
#include <cstring>
#include <mutex>
#include <thread>
#include <jni.h>

#define LOG(...) __android_log_print(ANDROID_LOG_ERROR, "FRIDA", __VA_ARGS__)

static GumScript *script = nullptr;
static std::once_flag gum_init_flag;
static std::atomic<bool> script_started{false};


static void on_message(const gchar *message, GBytes *data, gpointer user_data) {
    const char *key = R"("payload":")";

    const char *start = strstr(message, key);

    if (start) {
        start += strlen(key);

        const char *end = strchr(start, '"');

        if (end) {
            std::string payload(start, end - start);
            LOG("%s", payload.c_str());
            return;
        }
    }

    LOG("%s", message);
}

static void startFridaScript(const std::string &packageName, const std::string &jsSource) {
    bool expected = false;
    if (!script_started.compare_exchange_strong(expected, true)) {
        LOG("Script already started for this process, ignoring request for %s", packageName.c_str());
        return;
    }

    std::thread([packageName, jsSource]() {
        std::call_once(gum_init_flag, []() {
            gum_init_embedded();
        });

        GError *error = nullptr;
        GumScriptBackend *backend = gum_script_backend_obtain_v8();

        script = gum_script_backend_create_sync(
                backend,
                packageName.c_str(),
                jsSource.c_str(),
                nullptr,
                nullptr,
                &error
        );

        if (error) {
            LOG("Script compile error for %s: %s", packageName.c_str(), error->message);
            g_error_free(error);
            return;
        }

        gum_script_set_message_handler(script, on_message, nullptr, nullptr);
        gum_script_load_sync(script, nullptr);

        LOG("Script loaded for package: %s", packageName.c_str());

        GMainContext *context = g_main_context_default();
        while (true) {
            g_main_context_iteration(context, TRUE);
        }
    }).detach();
}

void onLibraryLoaded(const char *name, void *handle) {

}

extern "C"
__attribute__((visibility("default")))
__attribute__((used))
NativeOnModuleLoaded native_init(const NativeAPIEntries *entries) {
    LOG("native_init called");

    return onLibraryLoaded;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_fuck_iab_NativeBridge_startScript(
        JNIEnv *env, jclass /*clazz*/, jstring jPackageName, jstring jScriptSource) {

    const char *pkgChars = env->GetStringUTFChars(jPackageName, nullptr);
    const char *srcChars = env->GetStringUTFChars(jScriptSource, nullptr);

    std::string packageName(pkgChars);
    std::string scriptSource(srcChars);

    env->ReleaseStringUTFChars(jPackageName, pkgChars);
    env->ReleaseStringUTFChars(jScriptSource, srcChars);

    startFridaScript(packageName, scriptSource);
}