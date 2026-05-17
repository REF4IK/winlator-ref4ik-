#ifndef FPSLIMITER_H
#define FPSLIMITER_H

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Инициализация FPS limiter
 * fps_limit: целевой FPS (0 = без ограничения)
 */
void fps_limiter_init(int fps_limit);

/**
 * Установить новый FPS лимит
 */
void fps_limiter_set(int fps_limit);

/**
 * Вызывать в конце каждого кадра для ограничения FPS
 */
void fps_limiter_wait();

/**
 * Получить текущий FPS лимит
 */
int fps_limiter_get();

#ifdef __cplusplus
}
#endif

#endif // FPSLIMITER_H
