package com.windsor.fuckCn2b2t;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class I1lI {

    private final Set<String> lI11;
    private final Set<String> lI1l;
    private volatile Pattern lll1;

    private I1lI(Set<String> lI11, Set<String> lI1l) {
        this.lI11 = lI11;
        this.lI1l = lI1l;
    }

    public static I1lI l1lI() {
        Set<String> II1l = new HashSet<>(2048);
        Set<String> l1Il = new HashSet<>(256);
        Collections.addAll(II1l, I11I.IIl1());
        Collections.addAll(l1Il, I11I.l1lI());
        return new I1lI(II1l, l1Il);
    }

    public boolean II1I(String lI1I) {
        return lI1I != null && lI11.contains(lI1I);
    }

    public int I1ll() {
        return lI11.size();
    }

    public int Il1l() {
        return lI1l.size();
    }

    public Pattern llIl() {
        Pattern l1II = lll1;
        if (l1II == null) {
            synchronized (this) {
                l1II = lll1;
                if (l1II == null) {
                    l1II = lII1();
                    lll1 = l1II;
                }
            }
        }
        return l1II;
    }

    private Pattern lII1() {
        if (lI1l.isEmpty()) {
            return Pattern.compile("(?!x)x");
        }
        List<String> lIlI = new ArrayList<>(lI1l);
        lIlI.sort((I1I1, I1II) -> Integer.compare(I1II.length(), I1I1.length()));
        StringBuilder Il11 = new StringBuilder("(?:");
        for (int l11I = 0; l11I < lIlI.size(); l11I++) {
            if (l11I > 0) Il11.append('|');
            Il11.append(Pattern.quote(lIlI.get(l11I)));
        }
        return Pattern.compile(Il11.append(')').toString());
    }
}
