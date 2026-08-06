package com.nuono.next.competitoranalysis.dp08;

import com.nuono.next.datapull.orchestration.DataPullScopeKey;
import com.nuono.next.datapull.runtime.OperationCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/** Stable flattened member cursor; its logical prefix identifies one DP08 scope. */
final class Dp08MemberSourceCursor {
    private final OperationCode operation;
    private final long owner, watch, keyword, memberId;
    private final int memberOrder;
    private final String store, site, noonCode;

    private Dp08MemberSourceCursor(OperationCode op,long owner,long watch,long keyword,int order,
            long memberId,String store,String site,String noonCode){this.operation=op;this.owner=owner;
        this.watch=watch;this.keyword=keyword;this.memberOrder=order;this.memberId=memberId;
        this.store=store;this.site=site;this.noonCode=noonCode;}

    static Dp08MemberSourceCursor from(Dp08KeywordScopeRow row) {
        boolean self="SELF".equals(row.getTrackedProductType());
        return new Dp08MemberSourceCursor(OperationCode.DP08A,
                MyBatisDp08ScopeCatalog.positive(row.getOwnerUserId(),"ownerUserId"),
                MyBatisDp08ScopeCatalog.positive(row.getWatchProductId(),"watchProductId"),
                MyBatisDp08ScopeCatalog.positive(row.getKeywordId(),"keywordId"),self?0:1,
                self?0:MyBatisDp08ScopeCatalog.positive(row.getCompetitorProductId(),"competitorProductId"),
                MyBatisDp08ScopeCatalog.normalizeUpper(row.getStoreCode(),"storeCode"),
                MyBatisDp08ScopeCatalog.normalizeUpper(row.getSiteCode(),"siteCode"),null);
    }

    static Dp08MemberSourceCursor from(Dp08ListTargetRow row) {
        return new Dp08MemberSourceCursor(OperationCode.DP08B,
                MyBatisDp08ScopeCatalog.positive(row.getOwnerUserId(),"ownerUserId"),
                MyBatisDp08ScopeCatalog.positive(row.getWatchProductId(),"watchProductId"),0,0,
                row.getCompetitorProductId()==null?0:
                    MyBatisDp08ScopeCatalog.positive(row.getCompetitorProductId(),"competitorProductId"),
                MyBatisDp08ScopeCatalog.normalizeUpper(row.getStoreCode(),"storeCode"),
                MyBatisDp08ScopeCatalog.normalizeUpper(row.getSiteCode(),"siteCode"),
                MyBatisDp08ScopeCatalog.normalizeUpper(row.getNoonProductCode(),"noonProductCode"));
    }

    static Dp08MemberSourceCursor parse(OperationCode operation,String value) {
        if(value==null)return null;String[] f=value.split(":",-1);
        try {
            requireResumeSuffix(f);
            if(operation==OperationCode.DP08A && "DP08AM2".equals(f[0])) {
                String identity=decode(f[6]);int separator=identity.indexOf('\0');
                if(separator<1||separator==identity.length()-1)throw new IllegalArgumentException("bad DP08A identity");
                return new Dp08MemberSourceCursor(operation,Long.parseLong(f[1]),Long.parseLong(f[2]),
                        Long.parseLong(f[3]),Integer.parseInt(f[4]),Long.parseLong(f[5]),
                        identity.substring(0,separator),identity.substring(separator+1),null);
            }
            if(operation==OperationCode.DP08B && "DP08BM2".equals(f[0]))
                return new Dp08MemberSourceCursor(operation,Long.parseLong(f[1]),Long.parseLong(f[5]),0,0,
                        Long.parseLong(f[6]),decode(f[2]),decode(f[3]),decode(f[4]));
        } catch(RuntimeException ignored){throw new IllegalArgumentException("bad DP08 member cursor",ignored);}
        throw new IllegalArgumentException("bad DP08 member cursor");
    }

    String encode(){if(operation==OperationCode.DP08A)return "DP08AM2:"+owner+":"+watch+":"+keyword+":"+
            memberOrder+":"+memberId+":"+encode(store+'\0'+site);return "DP08BM2:"+owner+":"+encode(store)+":"+
            encode(site)+":"+encode(noonCode)+":"+watch+":"+memberId;}
    static String resume(String nativeCursor,String progressMarker){String cursor=Objects.requireNonNull(nativeCursor,"nativeCursor");
        String marker=Objects.requireNonNull(progressMarker,"progressMarker");if(cursor.isEmpty()||marker.isEmpty())
            throw new IllegalArgumentException("DP08 resume cursor parts are required");return cursor+":R:"+encode(marker);}
    String logicalCursor(){return operation==OperationCode.DP08A?"DP08A1:"+owner+":"+watch+":"+keyword:
            "DP08B1:"+owner+":"+encode(store)+":"+encode(site)+":"+encode(noonCode);}
    String scopeKey(){return operation==OperationCode.DP08A?DataPullScopeKey.from("dp08a",Long.toString(owner),
            store,site,Long.toString(watch),Long.toString(keyword)):DataPullScopeKey.from("dp08b",Long.toString(owner),
            store,site,noonCode);}
    boolean sameScope(Dp08MemberSourceCursor other){return other!=null&&scopeKey().equals(other.scopeKey());}
    long owner(){return owner;} long watch(){return watch;} long keyword(){return keyword;}
    int memberOrder(){return memberOrder;} long memberId(){return memberId;}
    String store(){return store;} String site(){return site;} String noonCode(){return noonCode;}
    private static void requireResumeSuffix(String[] fields){if(fields.length==7)return;
        if(fields.length!=9||!"R".equals(fields[7])||decode(fields[8]).isEmpty())
            throw new IllegalArgumentException("bad DP08 resume cursor");}
    private static String encode(String v){return Base64.getUrlEncoder().withoutPadding().encodeToString(v.getBytes(StandardCharsets.UTF_8));}
    private static String decode(String v){return new String(Base64.getUrlDecoder().decode(v),StandardCharsets.UTF_8);}
}
