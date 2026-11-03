package com.lesha_top1972.basedefense.entity.enemy.thunder_tiger;

/**
 * Тайминги «Громового тигра» в тиках. СОЗДАНО tools/thunder_tiger_gen.py — не править руками:
 * поменял анимацию → запусти скрипт, и сервер возьмёт новые моменты ударов.
 */
public final class TigerTimings {

    private TigerTimings() {
    }

    // double
    public static final int DOUBLE = 0;
    public static final int DOUBLE_LEN = 35;
    public static final int[] DOUBLE_HITS = {14, 21};

    // triple
    public static final int TRIPLE = 1;
    public static final int TRIPLE_LEN = 48;
    public static final int[] TRIPLE_HITS = {14, 21, 34};

    // double_blast
    public static final int DOUBLE_BLAST = 2;
    public static final int DOUBLE_BLAST_LEN = 43;
    public static final int[] DOUBLE_BLAST_HITS = {17, 30};
    public static final int DOUBLE_BLAST_SMASH = 17;
    public static final int DOUBLE_BLAST_DASH_FROM = 22;
    public static final int DOUBLE_BLAST_DASH_TO = 29;
    public static final int[] DOUBLE_BLAST_SHOTS = {17, 30};

    // triple_blast
    public static final int TRIPLE_BLAST = 3;
    public static final int TRIPLE_BLAST_LEN = 48;
    public static final int[] TRIPLE_BLAST_HITS = {18, 24, 34};
    public static final int TRIPLE_BLAST_DASH_FROM = 11;
    public static final int TRIPLE_BLAST_DASH_TO = 16;
    public static final int TRIPLE_BLAST_LEAP = 29;
    public static final int[] TRIPLE_BLAST_SHOTS = {18, 24, 34};

    // shatter
    public static final int SHATTER = 4;
    public static final int SHATTER_LEN = 60;
    public static final int[] SHATTER_HITS = {21, 32, 44};
    public static final int SHATTER_GLOW_FROM = 0;
    public static final int SHATTER_GLOW_TO = 48;
    public static final int SHATTER_LEAP = 37;

    // tangle
    public static final int TANGLE = 5;
    public static final int TANGLE_LEN = 60;
    public static final int TANGLE_CHARGE_FROM = 0;
    public static final int TANGLE_CHARGE_TO = 26;
    public static final int TANGLE_DASH_FROM = 26;
    public static final int TANGLE_DASH_TO = 30;
    public static final int TANGLE_LEAP = 30;
    public static final int TANGLE_SMASH = 35;
    public static final int[] TANGLE_HITS = {35};

    // stance
    public static final int STANCE = 6;
    public static final int STANCE_LEN = 30;

    // counter
    public static final int COUNTER = 7;
    public static final int COUNTER_LEN = 24;
    public static final int[] COUNTER_HITS = {8};
    public static final int[] COUNTER_SHOTS = {8};

    // reload
    public static final int RELOAD = 8;
    public static final int RELOAD_LEN = 32;
    public static final int RELOAD_OPEN = 4;
    public static final int RELOAD_EJECT = 6;
    public static final int[] RELOAD_INSERT = {10, 15, 19};
    public static final int RELOAD_CLOSE = 23;
    public static final int RELOAD_SPIN = 26;

    // reload_tac
    public static final int RELOAD_TAC = 9;
    public static final int RELOAD_TAC_LEN = 20;
    public static final int RELOAD_TAC_OPEN = 2;
    public static final int RELOAD_TAC_EJECT = 4;
    public static final int[] RELOAD_TAC_INSERT = {8};
    public static final int RELOAD_TAC_CLOSE = 12;

    // one_wait
    public static final int ONE_WAIT = 10;
    public static final int ONE_WAIT_LEN = 72;
    public static final int ONE_WAIT_PLANT = 9;

    // one_hit
    public static final int ONE_HIT = 11;
    public static final int ONE_HIT_LEN = 30;
    public static final int[] ONE_HIT_HITS = {14};
    public static final int ONE_HIT_PULL = 7;

    // stagger
    public static final int STAGGER = 12;
    public static final int STAGGER_LEN = 68;
    public static final int STAGGER_FALL = 7;
    public static final int STAGGER_RISE = 56;

    // phase2
    public static final int PHASE2 = 13;
    public static final int PHASE2_LEN = 48;
    public static final int PHASE2_CIGAR_OFF = 11;
    public static final int PHASE2_FLICK = 18;
    public static final int PHASE2_CRACK = 25;
    public static final int PHASE2_BURST = 36;

    // flurry_a
    public static final int FLURRY_A = 14;
    public static final int FLURRY_A_LEN = 13;
    public static final int[] FLURRY_A_HITS = {8};
    public static final int FLURRY_A_DASH_FROM = 3;
    public static final int FLURRY_A_DASH_TO = 9;

    // flurry_b
    public static final int FLURRY_B = 15;
    public static final int FLURRY_B_LEN = 13;
    public static final int[] FLURRY_B_HITS = {8};
    public static final int FLURRY_B_DASH_FROM = 3;
    public static final int FLURRY_B_DASH_TO = 9;

    // flurry_end
    public static final int FLURRY_END = 16;
    public static final int FLURRY_END_LEN = 52;
    public static final int FLURRY_END_JUMP = 5;
    public static final int[] FLURRY_END_HITS = {22};
    public static final int[] FLURRY_END_SHOTS = {22};

    // dodge_l
    public static final int DODGE_L = 17;
    public static final int DODGE_L_LEN = 9;
    public static final int DODGE_L_MOVE_FROM = 1;
    public static final int DODGE_L_MOVE_TO = 5;

    // dodge_r
    public static final int DODGE_R = 18;
    public static final int DODGE_R_LEN = 9;
    public static final int DODGE_R_MOVE_FROM = 1;
    public static final int DODGE_R_MOVE_TO = 5;

    // dodge_back
    public static final int DODGE_BACK = 19;
    public static final int DODGE_BACK_LEN = 10;
    public static final int DODGE_BACK_MOVE_FROM = 1;
    public static final int DODGE_BACK_MOVE_TO = 5;

    // dash
    public static final int DASH = 20;
    public static final int DASH_LEN = 14;
    public static final int DASH_DASH_FROM = 5;
    public static final int DASH_DASH_TO = 11;

    // kick
    public static final int KICK = 21;
    public static final int KICK_LEN = 20;
    public static final int[] KICK_HITS = {11};

    // backshot
    public static final int BACKSHOT = 22;
    public static final int BACKSHOT_LEN = 26;
    public static final int BACKSHOT_HOP_FROM = 1;
    public static final int BACKSHOT_HOP_TO = 11;
    public static final int[] BACKSHOT_SHOTS = {16};

    // death
    public static final int DEATH = 23;
    public static final int DEATH_LEN = 52;

    // taunt
    public static final int TAUNT = 24;
    public static final int TAUNT_LEN = 32;

    // twirl
    public static final int TWIRL = 25;
    public static final int TWIRL_LEN = 26;
    public static final int TWIRL_SPIN_FROM = 4;
    public static final int TWIRL_SPIN_TO = 19;

    // final_rise
    public static final int FINAL_RISE = 26;
    public static final int FINAL_RISE_LEN = 40;
    public static final int FINAL_RISE_FLARE = 16;

    /** Имена клипов по номеру действия (клиент). */
    public static final String[] NAMES = {"double", "triple", "double_blast", "triple_blast", "shatter", "tangle", "stance", "counter", "reload", "reload_tac", "one_wait", "one_hit", "stagger", "phase2", "flurry_a", "flurry_b", "flurry_end", "dodge_l", "dodge_r", "dodge_back", "dash", "kick", "backshot", "death", "taunt", "twirl", "final_rise"};
}
