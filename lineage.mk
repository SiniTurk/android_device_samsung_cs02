# Inherit some common CM stuff.
$(call inherit-product, vendor/cm/config/common_mini_phone.mk)

# Inherit device configuration
$(call inherit-product, device/samsung/cs02/device_cs02.mk)

# Device identifier. This must come after all inclusions
PRODUCT_DEVICE := cs02
PRODUCT_NAME := lineage_cs02
PRODUCT_BRAND := samsung
PRODUCT_MANUFACTURER := samsung
PRODUCT_MODEL := SM-G350
PRODUCT_CHARACTERISTICS := phone
