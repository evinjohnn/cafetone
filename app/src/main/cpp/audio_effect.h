#ifndef AUDIO_EFFECT_H
#define AUDIO_EFFECT_H

#include <stdint.h>
#include <sys/types.h>

#ifdef __cplusplus
extern "C" {
#endif

// --- STRUCT DEFINITIONS (in correct dependency order) ---

typedef struct effect_uuid_s {
    uint32_t timeLow;
    uint16_t timeMid;
    uint16_t timeHiAndVersion;
    uint16_t clockSeq;
    uint8_t  node[6];
} effect_uuid_t;

typedef struct audio_buffer_s {
    size_t   frameCount;
    union {
        void    *raw;
        float   *f32;
        int32_t *s32;
        int16_t *s16;
        uint8_t *u8;
    };
} audio_buffer_t;

typedef struct effect_interface_s {
    int32_t (*process)(struct effect_interface_s **self, audio_buffer_t *inBuffer, audio_buffer_t *outBuffer);
    int32_t (*command)(struct effect_interface_s **self, uint32_t cmdCode, uint32_t cmdSize,
                       void *pCmdData, uint32_t *replySize, void *pReplyData);
} effect_interface_t;


// --- LIBRARY ENTRY POINT ---

#define AUDIO_EFFECT_LIBRARY_TAG ((('A') << 24) | (('E') << 16) | (('L') << 8) | ('T'))
#define EFFECT_CONTROL_API_VERSION 0x00030000

typedef struct effect_descriptor_s {
    effect_uuid_t type;
    effect_uuid_t uuid;
    uint32_t      apiVersion;
    uint32_t      flags;
    uint16_t      cpuLoad;
    uint16_t      memoryUsage;
    char          name[64];
    char          implementor[64];
} effect_descriptor_t;

typedef int32_t (*effect_create_t)(const effect_uuid_t *uuid, int32_t sessionId, int32_t ioId, effect_interface_t **pItfe);
typedef int32_t (*effect_release_t)(effect_interface_t **itfe);
typedef int32_t (*effect_get_descriptor_t)(const effect_uuid_t *uuid, effect_descriptor_t *pDescriptor);

typedef struct audio_effect_library_s {
    uint32_t                tag;
    uint32_t                version;
    const char*             name;
    const char*             implementor;
    effect_create_t         create_effect;
    effect_release_t        release_effect;
    effect_get_descriptor_t get_descriptor;
} audio_effect_library_t;

// --- CONSTANTS ---

#define EFFECT_FLAG_TYPE_INSERT 0x00000000

enum {
    EFFECT_CMD_INIT                 = 0,
    EFFECT_CMD_SET_CONFIG           = 1,
    EFFECT_CMD_GET_CONFIG           = 2,
    EFFECT_CMD_RESET                = 3,
    EFFECT_CMD_ENABLE               = 4,
    EFFECT_CMD_DISABLE              = 5,
    EFFECT_CMD_SET_PARAM            = 6,
    EFFECT_CMD_GET_PARAM            = 9,
};

#define EINVAL 22
#define ENOMEM 12

#ifdef __cplusplus
}
#endif

#endif // AUDIO_EFFECT_H