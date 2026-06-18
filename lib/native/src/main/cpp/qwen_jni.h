#ifndef QWEN_JNI_H
#define QWEN_JNI_H

#include <vector>
#include <string>
#include "llama.h"

struct QwenState {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    std::vector<llama_token> cached_tokens;

    ~QwenState() {
        if (ctx) llama_free(ctx);
        if (model) llama_free_model(model);
    }
};

#endif
