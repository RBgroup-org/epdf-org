/*
 * ePDF Engine (epdf-org) — an open-source PDF engine and toolkit.
 * Part of the ePDF Engine project.  Copyright (c) 2026 Rupesh Borse.
 *
 * Licensed under the ePDF Engine License, a permissive open-source license:
 * you may freely use, copy, modify, and distribute this software (including in
 * commercial or closed-source products) provided this notice and the LICENSE
 * file are retained. See LICENSE at the repository root for the full terms.
 *
 * Original clean-room work: no third-party or copyleft (AGPL/GPL) code.
 * Cryptography is intentionally out of scope for epdf-org.
 *
 * Author: Rupesh Borse
 */
package com.epdfengine.rb.org.text.hyphen;

/**
 * A curated subset of the classic English (US) Liang/TeX hyphenation patterns.
 * These patterns are freely redistributable data (originally released by Liang and
 * bundled with TeX); they encode facts about where English words may be broken.
 * The set here is intentionally compact — enough for good general hyphenation — and
 * can be replaced or extended by supplying custom pattern data to {@link Hyphenator}.
 */
final class HyphenationPatterns {

    private HyphenationPatterns() {}

    static final String EN_PATTERNS =
            ".ach4 .ad4der .af1t .al3t .am5at .an5c .ang4 .ani5m .ant4 .an3te .anti5s " +
            ".ar5s .ar4tie .ar4ty .as3c .as1p .as1s .aster5 .atom5 .au1d .av4i .awn4 " +
            ".ba4g .ba5na .bas4e .ber4 .be5ra .be3sm .be5sto .bri2 .but4ti .cam4pe " +
            ".can5c .capa5b .car5ol .ca4t .ce4la .ch4 .chill5i .ci2 .cit5r .co3e .co4r " +
            ".cor5ner .de4moi .de3o .de3ra .de3ri .des4c .dictio5 .do4t .du4c .dum4be " +
            ".earth5 .eas3i .eb4 .eer4 .eg2 .el5d .el3em .enam3 .en3g .en3s .eq5ui5t " +
            ".er4ri .es3 .eu3 .eye5 .fes3 .for5mer .ga2 .ge2 .gen3t4 .ge5og .gi5a .gi4b " +
            ".go4r .hand5i .han5k .he2 .hero5i .hes3 .het3 .hi3b .hi3er .hon5ey .hon3o " +
            ".hov5 .id4l .idol3 .im3m .im5pin .in1 .in3ci .ine2 .in2k .in3s .ir5r .is4i " +
            ".ju3r .la4cy .la4m .lat5er .lath5 .le2 .leg5e .len4 .lep5 .lev1 .li4g .lig5a " +
            ".li2n .li3o .li4t .mag5a5 .mal5o .man5a .mar5ti .me2 .mer3c .me5ter .mis1 " +
            ".mis4ti .mon3e .mo3ro .mu5ta .muta5b .ni4c .od2 .odd5 .of5te .or5ato .or3c " +
            ".or1d .or3t .os3 .os4tl .oth3 .out3 .ped5al .pe5te .pe5tit .pi4e .pio5n .pi2t " +
            ".pre3m .ra4c .ran4t .ratio5na .ree2 .re5mit .res2 .re5stat .ri4g .rit5u .ro4q " +
            ".ros5t .row5d .ru4d .sci3e .self5 .sell5 .se2n .se5rie .sh2 .si2 .sing4 .st4 " +
            ".sta5bl .sy2 .ta4 .te4 .ten5an .th2 .ti2 .til4 .tim5o5 .ting4 .tin5k .ton4a " +
            ".to4p .top5i .tou5s .trib5ut .un1a .un3ce .under5 .un1e .un5k .un5o .un3u " +
            ".up3 .ure3 .us5a .ven4de .ve5ra .wil5i .ye4 4ab. a5bal a5ban abe2 ab5erd " +
            "abi5a ab5it5ab ab5lat ab5o5liz 4abr ab3rog ab3ul a4car ac5ard ac5aro a5ceou " +
            "ac1er a5chet 4a2ci a3cie ac1in a3cio ac5rob act5if ac3ul ac4um a2d ad4din " +
            "ad5er. 2adi a3dia ad3ica adi4er a3dio a3dit a5diu ad4le ad3ow ad5ran ad4su " +
            "4adu a3duc ad5um ae4r aeri4e a2f aff4 a4gab aga4n ag5ell age4o 4ageu ag1i " +
            "4ag4l ag1n a2go 3agog ag3oni a5guer ag5ul a4gy a3ha a3he ah4l a3ho ai2 a5ia " +
            "a3ic. ai5ly a4i4n ain5in ain5o ait5en a1j ak1en al5ab al3ad a4lar 4aldi " +
            "2ale al3end a4lenti a5le5o al1i al4ia. ali4e al5lev 4allic 4alm a5log. a4ly. " +
            "4alys 5a5lyst 5alyt 3alyz 4ama am5ab am3ag ama5ra am5asc a4matis a4m5ato " +
            "am5era am3ic am5if am5ily am1in ami4no a2mo a5mon amor5i amp5en a2n an3age " +
            "3analy a3nar an3arc anar4i a3nati 4and ande4s an3dis an1dl an4dow a5nee " +
            "a3nen an5est. a3neu 2ang ang5ie an1gl a4n1ic a3nies an3i3f an4ime a5nimi " +
            "a5nine an3io a3nip an3ish an3it a3niu an4kli 5anniz ano4 an5ot anoth5 " +
            "an2sa an4sco an4sn an2sp ans3po an4st an4sur antal4 an4tie 4anto an2tr " +
            "an4tw an3ua an3ul a5nur 4ao apar4 ap5at ap5ero a3pher 4aphi a4pilla ap5illar " +
            "ap3in ap3ita a3pitu a2pl apoc5 ap5ola apor5i apos3t aps5es a3pu aque5 2a2r " +
            "ar3act a5rade ar5adis ar3al a5ramete aran4g ara3p ar4at a5ratio ar5ativ a5rau " +
            "ar5av4 araw4 arbal4 ar4chan ar5dine ar4dr ar5eas a3ree ar3ent a5ress ar4fi " +
            "ar4fl ar1i ar5ial ar3ian a3riet ar4im ar5inat ar3io ar2iz ar2mi ar5o5d a5roni " +
            "a3roo ar2p ar3q arrel4 ar4sa ar2sh 4as. as4ab as3ant ashi4 a5sia. a3sib a3sic " +
            "5a5si4t ask3i as4l a4soc as5ph as4sh as3ten as1tr asur5a a2ta at3abl at5ac " +
            "at3alo at5ap ate5c at5ech at3ego at3en. at3era ater5n a5terna at3est at5ev " +
            "4ath ath5em a5then at4ho ath5om 4ati. a5tia at5i5b at1ic at3if ation5ar at3itu " +
            "a4tog a2tom at5omiz a4top a4tos a1tr at5rop at4sk at4tag at5te at4th a2tu at5ua " +
            "at5ue at3ul at3ura a2ty au4b augh3 au3gu au4l2 aun5d au3r au5sib aut5en au1th " +
            "a2va av3ag a5van ave4no av3era av5ern av5ery av1i avi4er av3ig av5oc a1vor " +
            "3away aw3i aw4ly aws4 ax4ic ax4id ay5al aye4 ays4 azi4er azz5i 5ba. bad5ger " +
            "ba4ge bal1a ban5dag ban4e ban3i barbi5 bari4a bas4si 1bat ba4z 2b1b b2be " +
            "b3ber bbi4na 4b1d 4be. beak4 beat3 4be2d be3da be3de be3di be3gi be5gu 1bel " +
            "be1li be3lo 4be5m be5nig be5nu 4bes4 be3sp be5str 3bet bet5iz be5tr be3tw " +
            "be3w be5yo 2bf 4b3h bi2b bi4d 3bie bi5en bi4er 2b3if 1bil bi3liz bina5r4 bin4d " +
            "bi5net bi3ogr bi5ou bi2t 3bi3tio bi3tr 3bit5ua b5itz b1j bk4 b2l2 blath5 b4le. " +
            "blen4 5blesp b3lis b4lo blun4t 4b1m 4b3n bne5g 3bod bod3i bo4e bol3ic bom4bi " +
            "bon4a bon5at 3boo 5bor. 4b1ora bor5d 5bore 5bori 5bos4 b5oto bo4t bound3 " +
            "4bp 4brit broth3 2b5s2 bsor4 2bt bt4l b4to b3tr buf4fer bu4ga bu3li bumi4 " +
            "bu4n bunt4i bu3re bus5ie buss4e 5bust 4buta 3butio b5uto b1v 4b5w 5by. bys4 " +
            "1ca cab3in ca1bl cach4 ca5den 4cag4 2c5ah ca3lat cal4la call5in 4calo can5d " +
            "can4e can4ic can5is can3iz can4ty cany4 ca5per car5om cast5er cas5tig 4casy " +
            "ca4th 4cativ cav5al c3c ccha5 cci4a ccompa5 ccon4 ccou3t 2ce. 4ced. 4ceden " +
            "3cei 5cel. 3cell 1cen 3cenc 2cen4e 4ceni 3cent 3cep ce5ram 4cesa 3cessi " +
            "ces5si5b ces5t cet4 c5e4ta cew4 2ch 4ch. 4ch3ab 5chanic ch5a5nis che2 " +
            "cheap3 4ched che5lo 3chemi ch5ene ch3er. ch3ers 4ch1in 5chine. ch5iness " +
            "5chini 5chio 3chit chi2z 3cho2 ch4ti 1ci 3cia ci2a5b cia5r ci5c 4cier 5cifi " +
            "ci4fl 4cig 5cin ci3nat cin3em c1ing c5ing. 5cino cion4 4cip ci3ph 4cipe " +
            "ci3pl 4cipr ci4p5ut ci4reg";

    static final String EN_EXCEPTIONS =
            "as-so-ciate as-so-ciates dec-li-na-tion oblig-a-tory phil-an-thropic " +
            "present presents project projects reci-procity re-cog-ni-zance ref-or-ma-tion " +
            "ret-ri-bu-tion ta-ble hy-phen-ation hy-phen-ate com-put-er com-put-ers " +
            "doc-u-men-ta-tion doc-u-ment al-go-rithm al-go-rithms con-ser-va-tion " +
            "beau-ti-ful in-for-ma-tion or-ga-ni-za-tion de-vel-op-ment en-gi-neer-ing " +
            "par-a-graph par-a-graphs man-age-ment en-vi-ron-ment";
}
