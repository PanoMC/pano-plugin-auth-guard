package com.panomc.plugins.authguard.error

import com.panomc.platform.model.Error

class CaptchaVerificationFailed : Error("CAPTCHA_VERIFICATION_FAILED", 403)
