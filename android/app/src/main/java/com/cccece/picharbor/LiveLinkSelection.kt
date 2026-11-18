package com.cccece.picharbor

/** Snapshot the user's transfer selection without changing backup preferences. */
internal fun selectedLinkMedia(all:List<MediaEntry>,ids:Set<String>):List<MediaEntry> =
    LivePhotoPairing.logical(all).filter{it.id in ids||LivePhotoPairing.findPair(it,all)?.id in ids}
