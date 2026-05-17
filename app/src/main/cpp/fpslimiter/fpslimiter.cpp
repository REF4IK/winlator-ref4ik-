#include "fpslimiter.h"
#include <time.h>
#include <unistd.h>
#include <stdint.h>
#include <android/log.h>

#define LOG_TAG "FPSLimiter"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Глобальные переменные для FPS limiter
static int64_t g_target_frame_time = 0;  // наносекунды на кадр
static int64_t g_frame_start = 0;
static int64_t g_frame_end = 0;
static int64_t g_overhead = 0;  // погрешность времени сна
static int g_fps_limit = 0;

/**
 * Получить текущее время в наносекундах
 */
static int64_t get_time_nano() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000000LL + (int64_t)ts.tv_nsec;
}

/**
 * Инициализация FPS limiter
 */
void fps_limiter_init(int fps_limit) {
    g_fps_limit = fps_limit;
    
    if (fps_limit > 0) {
        // Вычисляем целевое время одного кадра в наносекундах
        g_target_frame_time = 1000000000LL / fps_limit;
        LOGI("FPS Limiter initialized: %d FPS (%.3f ms per frame)", 
             fps_limit, g_target_frame_time / 1000000.0);
    } else {
        g_target_frame_time = 0;
        LOGI("FPS Limiter disabled (no limit)");
    }
    
    g_frame_end = get_time_nano();
    g_overhead = 0;
}

/**
 * Установить новый FPS лимит
 */
void fps_limiter_set(int fps_limit) {
    fps_limiter_init(fps_limit);
}

/**
 * Вычислить время сна
 */
static int64_t calc_sleep_time(int64_t start, int64_t end) {
    if (g_target_frame_time <= 0 || start <= 0)
        return 0;
    
    // Время, затраченное на обработку кадра
    int64_t work_time = start - end;
    if (work_time < 0)
        work_time = 0;
    
    // Вычисляем время сна с учётом погрешности
    int64_t sleep_time = (g_target_frame_time - work_time) - g_overhead;
    
    return sleep_time > 0 ? sleep_time : 0;
}

/**
 * Усыпить поток на указанное время
 */
static void do_sleep(int64_t sleep_nano) {
    if (sleep_nano <= 0)
        return;
    
    int64_t t0 = get_time_nano();
    
    // Конвертируем наносекунды в timespec
    struct timespec ts;
    ts.tv_sec = sleep_nano / 1000000000LL;
    ts.tv_nsec = sleep_nano % 1000000000LL;
    
    // Спим
    nanosleep(&ts, NULL);
    
    // Вычисляем погрешность (overhead)
    int64_t actual_sleep = get_time_nano() - t0;
    int64_t over = actual_sleep - sleep_nano;
    
    // Если погрешность разумная, учитываем её
    if (over > 0 && over < (g_target_frame_time / 2)) {
        g_overhead = over;
    } else {
        g_overhead = 0;
    }
}

/**
 * Вызывать в конце каждого кадра
 */
void fps_limiter_wait() {
    static int call_count = 0;
    
    if (g_target_frame_time <= 0) {
        if (call_count++ % 300 == 0) {
            LOGI("FPS Limiter: wait called but disabled (target=%lld)", (long long)g_target_frame_time);
        }
        return;  // FPS limiting отключен
    }
    
    g_frame_start = get_time_nano();
    
    // Вычисляем, сколько нужно спать
    int64_t sleep_time = calc_sleep_time(g_frame_start, g_frame_end);
    
    if (call_count++ % 300 == 0) {  // Логируем каждый 300-й вызов
        LOGI("FPS Limiter: wait (sleep=%lld ns, target=%lld ns)", 
             (long long)sleep_time, (long long)g_target_frame_time);
    }
    
    if (sleep_time > 0) {
        do_sleep(sleep_time);
    }
    
    g_frame_end = get_time_nano();
}

/**
 * Получить текущий FPS лимит
 */
int fps_limiter_get() {
    return g_fps_limit;
}
