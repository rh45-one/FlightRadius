package com.flightradius.app.ui.format

import com.flightradius.app.domain.AirspaceRule

private const val DEFAULT_RULE_ENGLISH_NAME = "Low and close"

/** The shipped default rule is stored with its English name; show it localised until renamed. */
fun AirspaceRule.displayName(): String =
    if (id == AirspaceRule.DEFAULT_ID && name == DEFAULT_RULE_ENGLISH_NAME) Words.get(W.RULE_DEFAULT_NAME)
    else name
