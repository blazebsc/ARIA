package org.blake7.aria;

public enum AriaStage {
    STAGE_1("The Assistant", 1, 3),
    STAGE_2("Too Close", 4, 7),
    STAGE_3("Corrupted", 8, Integer.MAX_VALUE);

    private final String name;
    private final int startDay;
    private final int endDay;

    AriaStage(String name, int startDay, int endDay) {
        this.name = name;
        this.startDay = startDay;
        this.endDay = endDay;
    }

    public String getName() { return name; }
    public int getStartDay() { return startDay; }
    public int getEndDay() { return endDay; }

    public static AriaStage fromDay(int day) {
        if (day >= 8) return STAGE_3;
        if (day >= 4) return STAGE_2;
        return STAGE_1;
    }

    public static AriaStage fromOrdinal(int ordinal) {
        AriaStage[] values = values();
        if (ordinal >= 0 && ordinal < values.length) return values[ordinal];
        return STAGE_1;
    }
}
