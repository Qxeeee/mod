package com.orbitcore.rocket;

/**
 * Этапы полёта ракеты. Сервер переключает их, клиент по ним строит кино:
 * планы камеры, субтитры, огонь и дым.
 */
public enum FlightPhase {
    /** Стоит на столе или лежит после посадки — не летит. */
    IDLE,
    /** Обратный отсчёт: отвод мачт, зажигание. */
    COUNTDOWN,
    /** Подъём: работают ступени, отработавшие отделяются. */
    ASCENT,
    /** Орбита: вид на Землю из иллюминатора. */
    SPACE,
    /** Перелёт к другому небесному телу. */
    TRANSFER,
    /** Управляемая посадка на тормозном двигателе. */
    DESCENT,
    /** Вход в атмосферу Земли: плазма, затем парашют. */
    REENTRY,
    /** Капсула после посадки. */
    LANDED;

    /** Летит ли ракета: экипажу нельзя выйти, работает кинокамера. */
    public boolean inFlight() {
        return this != IDLE && this != LANDED;
    }

    public static FlightPhase byId(int id) {
        FlightPhase[] values = values();
        return id >= 0 && id < values.length ? values[id] : IDLE;
    }
}
