package com.alibaba.lindorm.contest.compress;

import com.alibaba.lindorm.contest.compress.intcodec.simple.Simple9Codes;


import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

public class IntCompress {

    static int[] testNum2 = new int[]{
            1,1,1,1,1,1,1,1,1,1,
            1,1,1,1,1,1,1,1,1,1,
            1,1,1,1,1,1,1,1,1,1,
            1,1,1,1,1,1,1,1,1,1,
            1,1,1,1,1,1,1,1,1,1,
            1,1,1,1,1,1,1,1,1,1,
            1,1,1,1,1,1,1,1,1,1,
            1,1,1,1,1,1,1,1,1,1,
            1,1,1,1,1,1,1,1,1,1,
            1,1,1,1,1,1,1,1,1,1,
            1,1,1,1,1,1,1,1,1,1,
            1,1,1,1,1,1,1,1,1,1,
            1,1,1,1,1,1,513,1025
    };

    static int[] testNum = new int[]{
            442870,237290,290429,225740,78185,156354,26291,359786,512740,489735,78194,191322,489738,497193,489736,244882,147633,396459,244886,273063,61185,466837,436095,8776,420410,436090,26258,218008,176062,61205,202591,244925,88056,244919,312740,537581,52606,342523,427387,78129,95729,61300,78082,359706,52519,61249,61251,95691,182594,95690,225699,147652,420450,489830,202523,218075,156339,78107,405085,281746,140,537425,405077,512627,546291,17475,95520,152,321379,321376,78333,321380,321386,352142,321384,150,321362,168,202693,321364,512605,321366,175907,546259,321368,17507,321370,321372,321374,186,321345,105021,191,321351,321353,202713,105013,321355,512581,442691,321359,321335,321334,182754,225540,321332,95593,61420,321330,451421,321328,451410,321342,312670,321341,141003,304124,321340,193,321338,192,321318,321317,451407,321326,290479,321322,321320,78261,141023,239,290451,321298,156197,321308,112643,442643,321304,175981,442646,112645,537390,147539,217941,396360,290436,359816,225599,290444,141055,451205,245146,528544,245138,175767,412008,95418,202359,175763,202345,466616,294993,334551,202340,26541,264661,420646,130320,17912,295010,132439,288,405493,260459,435825,17894,17898,411993,365322,156571,365320,43702,365321,365326,365325,334478,489550,528619,365319,210000,420685,373114,365338,365337,365336,323,365331,365330,365329,365328,365335,365334,365333,122636,113069,190982,365332,520677,237468,442496,466627,321161,141173,60998,60999,474291,167054,8962,167041,537279,273367,70301,260563,435918,105244,87641,264518,34844,156498,34847,412136,9213,34846,528418,334412,61107,260569,87632,497573,78067,264529,392,260549,386,95271,26407,141194,260556,141239,17789,451112,432,528385,87670,245052,505336,365556,87663,273168,156518,505339,17773,182401,112964,290777,34848,312377,78019,342040,505228,442400,217724,497636,497635,34897,412070,210137,528511,202404,481982,373232,481983,130536,202403,130540,130535,9123,282074,427254,528496,130520,105337,130522,43548,156466,156468,237331,130512,52406,342094,282089,260537,282096,491,202383,282111,427220,290714,166925,167880,505461,475127,148104,264954,289877,226278,451004,351543,251904,121965,167898,334314,113361,496642,35760,69943,87543,96153,25756,96155,78682,419886,121983,43480,96158,245424,226254,382106,282143,520848,132717,155852,443380,60704,313240,18124,226246,443384,364628,264913,35721,443389,443363,419840,490247,364617,334287,167934,531,130614,87509,130618,140398,435508,611,313341,130624,546587,289808,419963,466398,419950,251987,419948,320899,382177,60771,272633,140361,183147,334232,583,412216,78635,78647,466401,451012,419911,18068,155801,25846,18071,289839,78652,546607,78648,35629,35625,147975,342806,226167,175410,520746,53241,404579,78801,78804,360434,70027,78824,43370,260803,342836,43369,43367,132847,190925,148015,78843,529198,35613,435662,390759,43384,282254,43376,8331,175463,303576,87335,140524,140523,404529,226095,536874,373447,520812,130774,167709,496836,373448,122033,113198,715,70092,148071,289973,512049,351710,202154,496883,428021,96101,35655,382020,342886,78782,25721,78783,167733,217457,226065,496877,320807,303590,320804,167729,78777,496865,78775,130800,725,496871,140506,210909,265197,69682,382378,265193,521123,435300,210489,237014,60443,69694,87292,190569,272793,536819,521139,320727,382388,210466,245665,35498,489988,175256,252223,489989,245663,489990,489991,245661,489986,52830,489992,265155,489993,420111,489995,245649,245645,130854,290169,360062,360048,8557,52815,245635,245632,450788,78356,521188,313069,427599,18361,175349,364834,225963,536756,260909,496982,260897,8498,435206,521152,182910,313037,404866,420160,156056,175323,87197,295425,856,295428,95995,60514,313040,18312,35533,236887,236881,78559,18299,69808,140730,69815,190719,245541,133068,87140,78585,43130,290285,351372,182975,190657,130985,295634,60581,175112,78560,245519,133102,133101,8600,148311,245618,427723,1020,43038,201876,427719,1011,245626,261053,113426,104814,78474,512273,245608,245613,95809,113470,52890,442914,427746,971,312912,201891,78510,113451,295576,78500,360123,289332,36299,44971,44969,236205,44966,36293,94688,44964,44962,305018,496242,139856,443816,503816,106205,175063,465895,366090,175064,253567,25289,490874,139880,226729,335805,289306,139885,94680,320388,44958,389248,44959,265380,373828,389258,113808,226754,280595,36238,320500,545150,320505,79231,79230,139798,79225,395431,521348,521403,351012,490793,209200,71476,94608,265240,403991,148601,60407,395364,435081,36189,389157,513578,343396,79235,351217,1262,320279,226595,373963,465733,79249,373961,192442,192445,86839,483213,148562,94555,183762,148566,351207,273978,1156,389237,452382,389239,36105,1164,545248,443754,343329,289496,443731,71604,9936,273929,139962,513604,265319,9949,496492,183415,289580,140120,25586,192004,265631,140104,148968,538258,504082,389567,131345,131344,192050,131347,244214,389506,140146,311521,389510,320128,51256,434722,335531,131353,131355,244222,544768,389517,209017,389521,389523,244198,320159,129348,304731,496427,208921,129334,10098,51280,59955,374061,504135,538330,413055,1290,59946,538329,25512,59949,504156,538322,358525,78948,274360,71169,86782,389572,183318,274304,389575,1338,157687,71225,16894,443589,389576,25481,86765,78922,174755,381770,513831,265483,281034,131516,148849,16667,131511,25464,183528,79011,289723,490707,473117,421850,304887,79021,289712,443392,281065,113940,78996,538157,490734,121785,60127,71416,25439,10137,265506,490743,490740,395602,25415,16672,490739,261547,434869,434874,131456,421884,490744,140191,25403,404303,274209,25407,148787,289767,114042,274213,490641,389502,201448,16708,35846,311326,129452,16713,157506,129450,51407,60078,86646,289736,496517,44638,289735,86630,129412,289753,1444,434928,86625,79053,289750,244007,139366,129606,273627,139373,244456,335288,129631,253011,105725,200983,218582,504354,444293,52017,444295,218586,24785,395971,149210,174585,24808,95210,304501,403600,139337,79679,9272,513195,191765,495723,131635,381104,504442,166854,304413,252939,95110,381099,381097,235741,166876,227320,381102,120948,312214,304438,24744,281107,244364,420885,343998,149164,538060,129596,79741,1552,139292,495651,79749,451956,413335,95045,421106,120994,139497,374480,95050,374473,201105,86329,16959,421078,139464,335124,395897,95081,395877,95094,36694,545708,156688,227094,244270,421044,79817,380978,281269,148998,444252,288977,312114,52208,52219,156745,129699,426934,86341,513144,389748,71059,319852,359405,139422,105494,71061,209817,421229,374598,149462,59485,281448,304200,530114,530118,312044,191526,244706,266165,70757,365880,200719,319650,139604,266118,139601,261908,131901,473733,9527,17301,139608,9514,139590,139591,495998,218340,9510,86147,149480,174282,513479,24976,396164,253211,343692,451770,1833,51819,79436,451762,451763,451760,105893,105882,482414,209431,86224,17364,79474,273836,79477,79479,261977,1808,166655,86218,79469,25002,273841,390136,319742,51782,157129,335067,289148,174221,426551,149421,139773,451694,139764,156980,513291,131984,114456,9629,174194,253398,530001,86054,304347,209641,200847,304350,304348,304339,114440,9612,304343,304340,464988,304341,79543,296586,443949,304352,389922,350401,350400,244559,266013,434334,9641,139716,105802,9645,166491,244543,396039,381216,139696,79579,44123,166487,166485,44102,51938,149261,473713,174112,311858,17259,244509,114551,9714,183996,244507,17242,311809,451598,79615,166504,79584,289279,200942,139663,464957,464956,413688,464958,464953,464955,11000,444779,403018,204789,33043,110707,89937,10999,158277,204770,227653,384543,10978,28195,530741,50631,50628,288451,288450,539496,288448,539503,33072,357839,539513,110666,438266,403062,522289,165199,270878,80314,367236,288429,288431,204730,120470,59376,227590,227591,50573,288432,72654,59370,227587,97644,288434,97645,158215,288445,50565,227596,288446,323388,227597,288447,97638,173902,128228,97639,173903,227595,110630,97637,227593,288443,80282,499401,522345,227644,464704,97621,374990,392210,165122,165123,97612,165124,165125,165126,165127,258208,418546,103010,279803,97606,367293,97607,220091,429348,429349,262349,464809,72473,374819,90055,165359,297307,480114,438102,297306,514810,530876,142849,279575,72457,2063,522402,444870,19706,288322,444879,165334,297315,142898,409672,149648,50558,33185,204618,367225,444892,28299,314800,180481,487748,120342,19614,10786,19597,539550,453593,530936,453599,2122,212289,80147,174075,110743,444807,444808,499274,288258,392324,220115,444818,323487,28355,149708,403130,80137,2157,189219,464859,403292,158530,80107,522517,134646,271143,403273,429220,367563,227420,357611,235330,2477,514908,41551,418741,110918,180365,41542,479956,242992,464393,180373,305800,429187,80082,250780,250782,50315,72384,103238,89605,314459,164911,258453,28539,297089,280015,514855,243038,72404,514848,11150,514841,110860,143333,72430,235267,219727,235277,19745,479881,11157,314476,11159,235290,2306,279835,262619,143114,262623,262620,357493,499514,288626,79982,211970,149922,111081,189020,429091,444655,262593,403403,134514,271270,437835,227543,340127,279871,219788,149917,79957,288581,58909,111067,444592,340216,2374,19846,158606,296981,262551,158597,97521,19858,158622,349774,72278,472204,243156,305760,72288,305757,403379,28610,158638,28621,514974,262576,158628,19883,158631,97492,487521,158645,250014,250015,384046,250010,250011,250008,297963,392786,445275,384048,20077,228192,315186,2724,27653,315193,297979,102426,2713,2714,98108,2708,366794,134908,102423,102422,173312,142466,228165,531256,250019,80866,72066,392826,315164,452887,531251,452973,472874,111133,270400,111132,111131,27743,20014,119978,33634,287903,394833,102497,243280,134833,204218,417991,445219,280268,287921,384086,150119,150112,134819,119946,452951,358330,322879,150122,134747,305420,89585,165850,280109,392911,80725,165852,258683,165853,234712,165855,80728,165842,51070,134736,157942,188789,243379,392900,479572,142379,2596,2594,287835,134733,10315,287826,479579,384137,89552,452992,384140,479585,402633,340899,384130,384133,2584,384135,212752,111328,280095,71937,71944,366674,287856,445316,10257,51001,270531,514185,315364,315385,249934,394972,89518,150271,98290,80690,20121,394990,522959,188688,119814,111271,315353,349530,80684,50926,150274,71855,288208,250243,499099,102688,314943,499088,120293,128902,445003,150293,150292,150294,50935,150299,2957,250277,507866,393079,358132,227904,89165,2953,349337,429749,270649,41085,33305,150326,150325,538695,71827,150335,507842,27954,2960,188580,102760,150336,150337,180930,173152,102756,227880,71934,173175,150356,150359,280547,128991,150358,120248,270661,150354,80534,150365,150364,150367,548482,150366,150361,150360,150363,120243,514363,150374,3018,479419,472605,150371,150368,111401,150369,128999,111396,212685,479400,180983,71896,120217,102744,173149,50833,180991,288174,212702,358058,349386,165579,20455,315069,111557,280377,58371,150402,28043,173240,514498,366959,180764,173235,165591,33465,180776,71682,58400,263131,50754,472787,50755,97962,58410,437312,263119,393194,263118,41202,384385,297543,135028,20387,384506,234880,158112,71798,270792,71794,10516,243709,548358,28119,288002,464112,288058,20353,452819,375672,58474,357938,315081,10546,234937,472711,102864,134967,212560,50715,80443,10556,428515,73683,211423,333581,11955,488911,401928,259212,341373,515634,287413,322354,127185,428485,3314,428484,164127,428490,349159,104032,203652,150592,18477,58312,203648,251576,119510,251578,119511,49624,393257,498352,271928,278681,498354,498355,278684,88899,263260,498358,278686,498359,498360,159298,49612,454436,150559,454437,287432,356811,159353,523301,454444,159351,251538,454445,454446,96542,454440,203732,454441,133326,454449,383547,34084,454448,133322,49638,211361,471419,133317,531743,103977,278709,287270,104144,368137,540551,251518,11827,11826,11820,393467,287281,3141,18567,263326,263327,211272,437012,437035,393416,150742,263329,221148,481029,89011,96725,18606,18605,383734,437042,419447,375902,523400,272032,531883,391393,356706,259159,531874,203647,211230,278542,298316,104083,34187,27304,42990,471516,18629,141833,18631,27303,428336,18630,540648,287297,89082,251392,445912,383666,34221,515804,298137,523614,322100,3534,322102,454237,322097,454239,322099,322098,251872,322093,96368,322094,322095,27508,322089,322090,127476,322091,73433,104280,73436,58104,287649,383867,383866,383859,73449,150850,463428,411010,81046,3570,42524,506281,12259,341046,142212,33805,150819,251814,81131,88651,471104,96309,181436,18770,471107,119774,322145,96316,12286,190163,454197,454196,333435,172587,454193,96256,12231,81102,12232,540283,181381,251781,111946,33836,88681,12243,49398,391497,73395,211132,540263,391499,454190,445519,133590,411080,341232,203302,181349,298012,3397,298014,287547,88710,49180,272358,436749,228498,18841,88720,151034,436739,463585,133438,436742,133437,133436,150981,515993,480794,3433,3434,498517,410897,96448,228523,278905,88762,18876,454378,203286,428096,18871,181284,516088,27560,480892,463549,119627,172684,73222,287608,546926,428072,18908,12156,428070,210971,428065,142105,278796,220863,133445,428061,133446,278835,133440,428058,428056,133452,428052,133455,133448,220815,133449,341144,127234,133450,428048,127235,42714,133460,133463,133462,234451,12125,234452,133456,234453,133458,540385,489441,3826,34675,497863,34676,57813,446210,489446,57817,436648,73210,489457,18979,18976,34661,348666,73187,34666,88367,286865,73193,18990,220484,506557,81802,251130,489408,251131,172378,251133,151163,242259,251122,251123,172371,251124,251125,251126,497904,446263,11424,220523,88325,158735,172365,88320,11430,112173,314193,489439,321844,357320,233566,286924,203224,26641,532238,26655,515149,42329,436716,97055,133844,428948,220423,436735,26633,19030,271401,73109,19026,233591,446313,463146,203250,189909,393776,436688,286970,333139,73099,263767,357367,26842,524010,158900,11282,203032,118837,314351,3691,211810,151236,471997,81673,127561,19106,524017,3687,11321,229266,97279,203058,73051,271589,242390,118805,112301,250976,81709,489310,164768,383198,57631,133718,540128,401636,220549,540150,97155,279100,322008,3618,211731,19163,127538,515298,489245,57642,189767,34692,357247,151213,134029,428759,103783,401712,220237,445983,251331,411544,81547,11661,26958,181953,260024,498112,81554,72946,134040,506793,141810,127981,19208,49803,271734,72906,42031,242501,259972,498155,182006,34398,159080,287196,42055,228973,228971,4003,81614,228963,287185,81612,4010,72882,26903,489122,42066,26899,181906,57500,287168,279459,251309,57505,498110,141697,81645,96813,251297,134116,34327,134142,524035,181938,159056,233857,242657,383478,251204,251206,133900,181839,133899,394189,159158,141680,229047,489058,229051,436256,251231,419167,251233,251237,515506,127846,251243,515505,
            515508,506645,81442,515500,27133,164541,314062,233925,172199,251136,287059,287071,172204,72754,333014,229059,392181,151464,229070,251181,251178,119109,42237,49755,233979,533216,205886,307727,56437,198227,171221,39103,524430,524432,101285,21986,400786,179874,248581,501063,21974,195161,195162,355429,533195,83672,316549,195155,524467,386557,47842,501073,233453,439897,160707,30637,431171,446595,501030,462530,21939,485481,470197,455409,446608,386461,198155,299062,56366,108938,39148,30665,66128,286030,186490,256284,470145,378831,439815,56339,386464,198192,299009,56320,416059,73771,533144,455215,440046,440047,73948,455211,455210,455208,455204,294859,485543,386376,541913,424893,145316,256480,65163,440061,431261,386384,108873,13260,198342,307863,145313,325204,455225,347838,213519,361467,440010,66203,171122,524327,91181,171105,4495,21837,213543,470100,4487,21829,56519,378721,126424,455276,386310,56503,151902,73865,145379,462427,439998,325137,30534,462419,118694,268603,277339,316537,416170,416171,431342,198322,101202,248830,248825,424901,470044,83467,101197,73902,30567,355678,56675,74054,299387,30341,316862,446941,221664,462744,368657,325576,213915,500837,83965,516264,144944,285720,30375,12905,347924,285714,325620,409287,4120,299331,151735,361024,83875,455669,455671,325530,248391,248390,455675,455674,21677,455677,171397,455679,180198,248385,248384,39412,386191,66421,355591,30425,431476,74021,508605,355635,316884,316881,66381,316880,198462,316877,316875,316872,415785,198455,316871,316868,198450,316866,91646,316867,470408,21659,316864,409231,316865,66476,100908,100907,455481,100906,66473,100903,100902,100901,12993,66465,100897,424621,338177,424619,424616,66490,198614,424614,446790,83826,100913,424604,256194,100878,118473,294647,233066,100864,4230,100892,268353,66462,100894,100895,13050,171378,66458,195538,299463,100884,186815,100886,440258,100880,30261,100883,233087,500925,285941,500919,348153,386079,508433,355715,485856,30291,415877,446778,206279,195463,446780,126190,66509,118403,74145,66497,478129,330497,446766,400492,83731,39255,145117,232958,100752,447205,369447,424204,30119,213165,447219,30116,30117,39559,30115,39553,447223,416629,232934,232935,213158,232932,30123,317075,30120,171750,232928,30121,56906,206398,118129,47317,232926,30097,248085,248086,12634,387015,30106,4901,4903,286526,65574,118118,56943,56942,137027,100779,56940,12618,187005,221848,447136,4954,22431,22430,12604,137007,47265,317150,492924,74283,286549,462018,179455,206458,462019,408997,355853,83125,179452,416513,462020,100860,83133,152528,401364,221865,213198,194601,100844,65642,47247,532556,424329,416745,100638,179221,424333,269133,30002,532546,126901,532544,47219,232807,65673,137184,232804,486041,39434,241420,118214,232811,416715,64666,5045,232799,360943,109399,408954,408955,386899,324690,39466,57062,408945,447062,126854,22298,187125,22299,65759,22296,524904,22297,47165,22301,179295,194712,5077,64745,532510,550103,47142,83006,74399,248273,57017,160048,269093,65784,118197,126932,137119,57015,386824,187097,454780,431835,454779,39534,454776,454777,454774,256936,241519,100480,542591,65800,542585,501338,256579,416353,47607,172026,247870,47613,144402,100506,100509,100508,100511,100510,471030,247818,471027,4641,12353,187150,241319,144417,109260,286256,39851,152217,39862,276619,299883,109268,74583,117883,12383,307462,179639,330143,286297,330139,109216,64870,330137,187240,206658,416318,330133,330130,286293,159874,241351,152317,213498,247930,12340,299791,525287,83357,39896,136759,247925,74552,12350,39906,47494,39913,117805,424061,416263,74515,378087,276675,401100,276672,92126,4729,152276,470957,47471,307636,136933,179462,39689,454931,22084,179466,286367,360668,57283,206741,179473,386661,338725,179477,386669,64909,542680,532854,195049,369311,206772,39743,206769,286369,386634,439778,64918,276492,516641,386636,144580,100431,194946,100424,100427,187361,100420,100423,57216,256651,198062,286400,386599,439693,329988,286403,100440,317253,286406,486349,386594,117909,144605,286408,386604,100437,386607,100438,276586,286411,100432,4821,100433,179547,100434,432095,100460,100456,386591,532784,4832,100452,4833,100450,100478,268832,100475,57276,117944,100473,12444,100470,100471,100468,100467,100464,100465,307663,540685,540686,540681,315616,540677,369999,509852,469180,64067,232329,204885,197174,300039,48824,67167,324267,438809,102345,232354,170149,232353,549458,82566,417100,461453,170192,82677,152991,232411,144184,196216,102322,240032,500089,152961,14145,152973,5410,74818,257391,461480,144146,222422,461477,461478,64040,29611,447742,144131,293750,135521,135532,144140,232429,20789,64213,20799,308933,67326,362406,517482,517492,408373,205035,308954,385327,67285,29554,5595,517449,152939,385339,178760,239950,20740,64237,102216,478905,20746,135591,492537,278281,197338,354507,346793,278274,278276,517423,39996,278278,125312,125313,278293,74997,469066,486540,500175,339005,469074,67207,232289,461372,430265,461373,55501,461374,20813,179171,239845,13835,222624,425586,492119,278238,109696,29380,278237,239853,278236,222633,278234,408224,109718,499745,13840,82826,20620,215012,408218,29418,170404,534421,461813,540977,540978,117255,278240,461803,324512,278243,525792,278248,161439,278250,369686,40360,249346,205091,249344,249350,354642,249348,461712,143918,249354,40354,461714,249359,499819,117374,257138,461705,461707,284711,461709,196465,284710,29341,461711,377505,55679,461696,90518,29335,461701,461703,324559,20682,29354,205063,469457,187693,249387,179082,447986,269535,161483,49145,534475,143895,40351,49149,300357,179088,20689,197499,239775,179096,293486,152651,541077,416913,499892,369875,20526,48911,315760,430529,315756,499877,385039,362158,67573,90453,20531,269353,55737,144119,399438,101953,90466,29284,82694,205254,461695,461692,461691,354740,82707,125171,269326,517196,369895,40229,144038,135364,144036,525596,549820,447812,525568,205246,534379,5307,284834,541131,205234,197588,14048,170351,339258,214831,29225,417020,399421,479229,49005,232058,64442,125112,135990,135991,135989,21407,161171,135987,308327,461039,361728,135985,143709,135994,135995,135992,135993,205407,135975,135974,153572,125802,400374,518097,75297,63588,170666,214224,270243,66678,153540,257824,407999,277971,75274,13569,257833,117028,117031,13570,13572,170635,222895,21415,13574,222893,500558,125755,187955,222931,21463,214205,486913,469724,293235,323836,323832,469743,385988,63514,136023,425249,469732,385994,323788,385995,170718,277899,196679,214151,240544,55911,125698,323802,249338,300682,249336,48180,249342,354987,249340,75453,178262,90727,377176,75436,346324,21296,285672,377198,417665,117168,448000,214109,81983,257968,448011,407850,90713,75399,285688,153423,448021,90692,63681,63684,6118,21289,21290,136064,6126,455683,63671,455682,63665,222808,460835,63667,455686,460846,455692,438477,63672,330823,63674,231791,90660,40448,136175,143759,125867,66697,509308,430779,6028,90666,455710,222786,90642,66738,330857,117235,205496,300777,330849,270183,231767,75456,13760,13762,430746,400121,170912,248935,214506,160911,526312,13374,355104,456131,500231,28874,346480,48512,178670,292889,116770,75520,116769,188235,205683,28894,461263,143486,5750,135706,143482,214495,231648,430899,5632,205578,143374,407754,48633,500295,526241,82390,178592,257638,541533,491546,491547,28811,188164,491544,13385,248833,491545,248832,491543,75584,438645,91013,91015,223221,500330,28821,56176,361582,500371,249067,370424,67011,370426,346584,63976,63977,517724,285399,249063,63986,478634,56219,240208,517709,316231,125688,67044,300988,448274,101486,231426,269877,491714,285418,143612,478596,48406,285420,385544,491722,21048,425195,178545,63967,110105,549271,90913,160845,469841,301016,116937,75752,28712,461092,40721,197109,277542,300994,323938,277536,300992,469877,438772,407675,277522,56298,308620,28692,240188,417478,143551,240182,63899,28697,318517,495508,93186,68259,275229,177704,254449,200400,54524,475842,475843,106837,475841,54512,475846,318510,246684,318509,153882,475845,371126,116679,106852,15359,15358,526381,388449,208024,543975,526372,68331,223277,75905,457332,543890,371138,116671,518500,327181,99190,54449,153953,85519,15272,162565,223245,6594,353468,23810,318554,138676,275309,275307,99156,153998,32646,254307,6445,345648,153986,231367,380846,124178,292675,116607,503120,169198,363351,32686,353394,124212,15223,45814,353376,309854,483444,23968,460497,318710,318709,254247,138502,266668,413959,266666,75797,162750,207993,254260,200215,147315,284006,169111,200211,441873,177868,275448,154082,266642,162693,301068,413984,318664,15154,327332,147282,254226,223376,238647,230996,184735,32273,254206,184730,380475,370842,318782,124032,327521,380417,68503,184767,526624,442311,327530,184764,184761,85841,106607,45928,6285,457496,388210,106603,138477,68484,184748,433588,68485,230934,230930,327427,184791,178040,275017,37235,336210,230917,448795,76171,230915,310225,184773,283902,457586,457589,230922,544175,184823,254096,63481,23579,98896,184825,476065,328460,178013,422617,476094,32366,413885,177989,85762,98883,6195,254075,495110,54647,301412,178092,283711,266492,292434,301424,238753,68392,510684,14964,85970,336294,85982,23770,275108,476013,476019,200558,442192,14953,336310,283670,238727,442146,14867,442151,68474,37374,153814,68478,85950,68479,301372,106624,76035,106630,162465,526820,275182,238809,54559,54556,283714,106656,353599,178119,518362,169568,327767,162123,267102,146827,223820,267098,336446,239368,267097,177168,274726,162134,284546,267074,354022,107378,246205,62607,85097,336401,456766,327798,139204,456767,414684,267121,354004,406900,406901,239400,267109,85115,267111,267110,223858,267104,267107,267106,267117,267113,267112,62609,267115,7114,192644,139175,441502,85006,7113,475326,267036,76468,54931,139197,456769,45108,456768,45109,456771,62711,456770,177251,208616,274780,503732,327739,422387,422380,139157,406831,98682,292230,76440,326667,301736,534562,422375,468519,267052,380266,14694,292221,24527,98696,239492,468697,67597,116045,267217,326892,62518,14709,32179,456838,326884,326886,449260,422159,124673,139081,215182,326877,24550,494871,85217,139082,199756,468723,139085,139087,139086,139073,139072,519103,139077,139079,449244,362857,327914,24565,24567,24566,380333,326856,199768,169682,85240,177328,124700,24569,24568,24571,24570,534675,519122,319195,406932,146763,422227,326844,76335,67663,54799,139070,326825,336622,139068,422210,177365,274914,85151,93950,76351,169654,422219,93945,254762,162217,433744,62539,389014,433755,319221,459969,32210,274891,380392,116019,449160,433736,98328,245941,309669,107132,266816,460070,62902,292077,441799,503490,14572,449407,192967,62888,185258,266845,362719,93977,326977,362727,68028,185241,24179,345521,185216,245894,6829,200130,161897,161903,37664,254699,185230,379965,98393,309738,475555,441732,388654,434144,115853,76716,200096,475583,107044,185324,68034,115841,388667,115843,31835,319331,85310,124636,475520,284394,200091,98421,200094,192933,98409,457083,98402,345595,327149,354159,138878,67861,154290,208656,336805,161993,230638,124450,475507,98440,37773,388853,239232,510146,457124,85492,362605,301930,274564,115839,208689,31896,37797,371219,162016,6699,510168,62832,274666,327087,169913,6747,380099,441622,518869,37830,534934,388789,274673,457182,274672,484184,169892,185189,510138,98511,94189,85384,177662,371272,55096,67965,146548,94174,154324,230551,45440,146543,266939,208751,544539,62795,266932,167945,53410,317563,16257,247753,76933,137600,176737,176743,216640,167961,255419,199319,224311,326149,31583,519528,440976,84480,329242,92258,440983,352424,155002,329224,199359,123376,37968,16319,31615,494573,22886,146343,511808,423845,31509,22902,317470,494523,467033,16363,137703,406361,115657,137726,176669,107893,137722,237853,107902,137714,291815,123321,84570,22876,199415,107914,100330,92360,100332,155076,100333,100334,155078,100335,46729,199168,100320,276445,291612,137480,100324,107909,16128,100327,302137,230293,46749,115516,69246,230289,46743,137496,100336,100338,494401,107925,53299,230298,22926,62057,310899,76837,22913,123258,100315,449706,100313,230322,459494,46771,146268,459418,344628,155015,440955,69162,326360,291675,155018,76864,92318,155030,146227,16211,458402,155033,344617,168164,16234,276400,458392,100235,449781,168172,176780,53312,168173,168174,168175,449777,16230,449778,100225,168171,449775,168177,449769,168176,216756,168179,449771,415080,168178,224470,146206,185406,22997,423680,84785,53681,137372,387079,16026,16006,501940,329524,527716,146138,137405,107574,16051,46896,16050,16061,22557,317788,69569,501905,99909,199595,237640,501907,379474,77229,199644,53751,137433,247453,99889,16090,137428,501998,176947,99875,38183,69539,22634,199617,62346,511582,137410,22637,224606,77310,69533,501963,423567,501965,99866,302532,207244,137443,194497,237574,53567,494152,31448,441121,7286,53557,31443,494146,31442,494145,31441,38386,46989,15884,397523,22694,397520,31426,22688,185679,230025,458698,69466,69467,387234,7262,224661,22681,217074,154871,22677,291372,154875,22678,7248,199483,397542,100055,154879,168381,22674,441114,47016,458707,47015,145995,441195,137296,441188,22769,53618,163580,77136,441186,22763,326611,47050,145953,527763,137292,53605,458674,414801,247344,494123,185653,31421,137329,247350,47089,494114,122928,441182,53579,53577,53576,311094,137315,484637,494128,15968,53570,38272,107751,53574,168427,494135,406226,53572,138158,398194,83970,23300,115086,23314,23315,23318,310508,23319,23316,23317,23320,77502,23321,46139,328704,318022,15804,38495,458834,23329,379261,115110,61640,216129,77453,68842,31053,145914,328740,535596,23357,302759,145907,457745,77537,216108,31013,502739,457750,337470,519965,99592,363991,15850,155450,268110,363976,15861,229756,15860,108410,145811,99611,458793,138230,168565,15813,398098,255982,283582,8098,467583,61578,275729,457788,8107,302831,216091,255994,68795,61596,216080,99790,302608,450238,328861,92900,163211,185963,216302,520138,68703,99803,318150,77366,476198,77363,31220,520185,92878,337619,68714,440376,61516,291095,405943,31171,61504,185928,229775,255790,163255,99832,318190,68723,328913,61485,92842,207366,68620,363860,398259,388094,247082,38557,15743,176275,415585,61488,168703,229828,275861,115048,275863,432665,108491,115052,450259,15686,46274,275864,275867,450260,108505,476237,155539,31128,145719,283426,7988,450240,520096,108501,229424,162837,137900,145615,485335,344534,318294,23053,423128,93034,93033,450361,363694,247001,372426,216412,61910,229405,440744,38780,38782,476563,137864,325907,493789,255708,310703,433056,387691,493735,77815,535884,440779,318212,476648,283266,15589,99330,267864,114888,502494,387700,329069,229465,114938,30751,23164,61855,137937,23163,23160,23161,
            137940,123524,137933,267892,423102,7848,283313,543529,23193,23192,23195,318401,225168,23197,379073,23196,23199,23198,162973,543522,511140,23189,23191,23190,168871,511154,246893,353029,23226,99581,387717,440617,275655,123480,493634,440612,99573,108179,23210,23211,77583,23208,387732,23209,229511,23212,23213,23202,528338,23203,23200,99557,23201,23206,23207,99552,23204,519924,23205,93115,318343,23256,168950,415342,23253,23252,23255,229631,23254,23249,23251,23250,511229,23244,163009,23246,54093,23247,7694,23240,155300,23241,23242,511226,23243,23236,23237,23239,23232,23234,238218,23235,207668,275591,137808,268002,536039,379049,99504,155288,137822,93076,137823,432920,363634,379058,99499,38829,84458,485177,99503,68910,467952,440694
    };

    static int[] testNum3 = new int[]{24,237,114,246,71, 15, 70,27,243,97,220,77,85,20,38,36,30,100,83,84,160,227,239,81,197,186,108,18,113
    };

    static int[] testNum5 = new int[]{1,-3,5,-2,4
    };

    static int[] testNum4 = new int[1575];
    static  {
        Random random = new Random();
        for (int i = 0; i < 1575; i++) {
            testNum4[i] = Math.abs(random.nextInt(30000));
        }
    }


    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        int[] data = testNum4.clone();
        final byte[] compress = compress(data);
        System.out.println("cost:" + (System.currentTimeMillis() - start) + " ms");
        final int[] decompress = decompress(compress);
        boolean b = Arrays.equals(testNum4, decompress);
        System.out.println(b);
    }

    public static byte[] compress(int[] ints) {
//        int[] gapArray = toGapArray(ints);
//        for (int i = 0; i < gapArray.length; i++) {
//            gapArray[i] = ZigZagUtil.intToZigZag(gapArray[i]);
//        }
        int[] ints1 = Simple9Codes.innerEncode(ints);
        ByteBuffer allocate = ByteBuffer.allocate(ints1.length * 4);
        for (int i : ints1) {
            allocate.putInt(i);
        }
        final byte[] array = allocate.array();
        final GzipCompress gzipCompress = new GzipCompress();
        return gzipCompress.compress(array);
    }

    protected static int[] toGapArray(int[] numbers) {
        int prev = numbers[0];
        for (int i = 1; i < numbers.length; i++) {
            int tmp = numbers[i];
            numbers[i] = numbers[i] - prev;
            prev = tmp;
        }
        return numbers;

    }

    public static int[] decompress(byte[] bytes) {
        final GzipCompress gzipCompress = new GzipCompress();
        final byte[] bytes1 = gzipCompress.deCompress(bytes);
        final ByteBuffer wrap = ByteBuffer.wrap(bytes1);
        int[] ints = new int[bytes1.length / 4];
        for (int j = 0; j < ints.length; j++) {
            ints[j] = wrap.getInt();
        }
        final int[] decode = Simple9Codes.decode(ints);
//        for (int i = 0; i < decode.length; i++) {
//            decode[i] = ZigZagUtil.zigzagToInt(decode[i]);
//        }
//        for (int i = 1; i < decode.length; i++) {
//            decode[i] = decode[i - 1] + decode[i];
//        }
        return decode;
    }
}
