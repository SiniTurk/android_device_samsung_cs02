# Release name
PRODUCT_RELEASE_NAME := cs02

# Inherit some common CM stuff.
$(call inherit-product, vendor/cm/config/common_full_phone.mk)

# Inherit device configuration
$(call inherit-product, device/samsung/cs02/device_cs02.mk)

## Device identifier. This must come after all inclusions
PRODUCT_DEVICE := cs02
PRODUCT_NAME := cm_cs02
PRODUCT_BRAND := samsung
PRODUCT_MODEL := cs02
PRODUCT_MANUFACTURER := samsung
