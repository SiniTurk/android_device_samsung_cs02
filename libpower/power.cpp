/*
 * Copyright (c) 2015 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <errno.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

#include <utils/SystemClock.h>

#define LOG_TAG "PowerHAL"
#include <utils/Log.h>

#include <hardware/hardware.h>
#include <hardware/power.h>

struct local_power_module {
    struct power_module base;
};

#define BUF_SIZE 80

#define HISPEED_FREQ_PATH "/sys/devices/system/cpu/cpufreq/interactive/hispeed_freq"
#define HISPEED_FREQ_OFF  "800000"
#define TOUCHBOOST_PATH   "/sys/devices/system/cpu/cpufreq/interactive/boostpulse"
#define TOUCHBOOST_MIN_INTERVAL_MS 200

static int64_t last_touchboost;
static char hispeed_freq[BUF_SIZE];

static int sysfs_read(char *path, char buf[BUF_SIZE]) {
    int len;
    int fd = open(path, O_RDONLY);

    if (fd < 0) {
        strerror_r(errno, buf, sizeof(buf));
        ALOGE("Error opening %s: %s\n", path, buf);
        return fd;
    }

    len = read(fd, buf, BUF_SIZE-1);
    if (len < 0) {
        strerror_r(errno, buf, sizeof(buf));
        ALOGE("Error read from %s: %s\n", path, buf);
    } else {
        buf[len] = '\0';
    }

    close(fd);
    return len;
}

static void sysfs_write(const char *path, const char *const s) {
    char buf[BUF_SIZE];
    int len;
    int fd = open(path, O_WRONLY);

    if (fd < 0) {
        strerror_r(errno, buf, sizeof(buf));
        ALOGE("Error opening %s: %s\n", path, buf);
        return;
    }

    len = write(fd, s, strlen(s));
    if (len < 0) {
        strerror_r(errno, buf, sizeof(buf));
        ALOGE("Error writing to %s: %s\n", path, buf);
    }

    close(fd);
}

void power_set_interactive(struct power_module *, int on) {
    if (on) {
        if (hispeed_freq[0]) {
             sysfs_write(HISPEED_FREQ_PATH, hispeed_freq);
        }
    } else {
        if (sysfs_read(HISPEED_FREQ_PATH, hispeed_freq) < 0) {
             hispeed_freq[0] = '\0';
        }
        sysfs_write(HISPEED_FREQ_PATH, HISPEED_FREQ_OFF);
    }
}

static void touchboost() {
    int64_t now = android::uptimeMillis();
    if (now - last_touchboost > TOUCHBOOST_MIN_INTERVAL_MS) {
        sysfs_write(TOUCHBOOST_PATH, "1");
        last_touchboost = now;
    }
}

static void power_hint(struct power_module *module, power_hint_t hint, void *data)
{
    struct local_power_module *pm = (struct local_power_module *) module;
    int duration;

    switch (hint) {
    case POWER_HINT_INTERACTION:
        touchboost();
        break;

    case POWER_HINT_CPU_BOOST:
        duration = data != NULL ? (int) data : 1;
        touchboost();

    case POWER_HINT_VSYNC:
        break;

    default:
        break;
    }
}

static void power_init(struct power_module *)
{
}

static struct hw_module_methods_t power_module_methods = {
    .open = NULL,
};

struct local_power_module HAL_MODULE_INFO_SYM = {
    base: {
        common: {
            tag: HARDWARE_MODULE_TAG,
            module_api_version: POWER_MODULE_API_VERSION_0_2,
            hal_api_version: HARDWARE_HAL_API_VERSION,
            id: POWER_HARDWARE_MODULE_ID,
            name: "Hawaii Power HAL",
            author: "The CyanogenMod Project",
            methods: &power_module_methods,
        },
       init: power_init,
       setInteractive: power_set_interactive,
       powerHint: power_hint,
    },
};