package com.heru.prototype.data;

import androidx.annotation.NonNull;
import androidx.room.*;
import java.util.List;

/** Core family data is stored together so a completed activity and its star cannot diverge. */
@Database(entities = {HeruDatabase.ChildRow.class, HeruDatabase.GoalRow.class,
        HeruDatabase.ActivityRow.class, HeruDatabase.RecordRow.class,
        HeruDatabase.StarRow.class, HeruDatabase.MetaRow.class}, version = 1, exportSchema = true)
public abstract class HeruDatabase extends RoomDatabase {
    public abstract FamilyDao family();

    @Entity(tableName = "children")
    public static class ChildRow {
        @PrimaryKey @NonNull public String id = "";
        @NonNull public String name = "";
        @NonNull public String plans = "{}";
        public long createdAt;
    }

    @Entity(tableName = "goals", foreignKeys = @ForeignKey(entity = ChildRow.class,
            parentColumns = "id", childColumns = "childId", onDelete = ForeignKey.CASCADE))
    public static class GoalRow {
        @PrimaryKey @NonNull public String childId = "";
        @NonNull public String id = "";
        @NonNull public String title = "";
        public String imageUri;
        public int target;
        public int startStars;
        public boolean celebrated;
        public boolean celebrationPending;
    }

    @Entity(tableName = "activities", indices = @Index(value = {"childId", "routine"}, unique = true),
            foreignKeys = @ForeignKey(entity = ChildRow.class, parentColumns = "id",
                    childColumns = "childId", onDelete = ForeignKey.CASCADE))
    public static class ActivityRow {
        @PrimaryKey @NonNull public String id = "";
        @NonNull public String childId = "";
        @NonNull public String routine = "MEAL";
        @NonNull public String payload = "{}";
        public long startedAt;
    }

    @Entity(tableName = "records", indices = @Index(value = {"childId", "finishedAt"}),
            foreignKeys = @ForeignKey(entity = ChildRow.class, parentColumns = "id",
                    childColumns = "childId", onDelete = ForeignKey.CASCADE))
    public static class RecordRow {
        @PrimaryKey @NonNull public String id = "";
        @NonNull public String childId = "";
        @NonNull public String payload = "{}";
        public long finishedAt;
    }

    @Entity(tableName = "stars", indices = @Index("childId"),
            foreignKeys = @ForeignKey(entity = ChildRow.class, parentColumns = "id",
                    childColumns = "childId", onDelete = ForeignKey.CASCADE))
    public static class StarRow {
        @PrimaryKey @NonNull public String id = "";
        @NonNull public String childId = "";
        public long earnedAt;
        public int amount;
    }

    @Entity(tableName = "metadata")
    public static class MetaRow {
        @PrimaryKey @NonNull public String key = "";
    }

    @Dao
    public interface FamilyDao {
        @Query("SELECT * FROM children ORDER BY createdAt, id") List<ChildRow> children();
        @Query("SELECT * FROM goals WHERE childId = :childId LIMIT 1") GoalRow goal(String childId);
        @Query("SELECT * FROM activities WHERE childId = :childId ORDER BY startedAt DESC") List<ActivityRow> activities(String childId);
        @Query("SELECT * FROM records WHERE childId = :childId ORDER BY finishedAt DESC") List<RecordRow> records(String childId);
        @Query("SELECT COALESCE(SUM(amount), 0) FROM stars WHERE childId = :childId") int stars(String childId);
        @Query("SELECT COUNT(*) FROM metadata WHERE `key` = :key") int imported(String key);
        @Query("SELECT COUNT(*) FROM records WHERE id = :id") int hasRecord(String id);
        @Upsert void putChild(ChildRow row);
        @Insert(onConflict = OnConflictStrategy.REPLACE) void putGoal(GoalRow row);
        @Upsert void putActivity(ActivityRow row);
        @Insert(onConflict = OnConflictStrategy.IGNORE) long putRecord(RecordRow row);
        @Insert(onConflict = OnConflictStrategy.IGNORE) long putStar(StarRow row);
        @Insert(onConflict = OnConflictStrategy.IGNORE) void putMeta(MetaRow row);
        @Query("DELETE FROM activities WHERE id = :id") void removeActivity(String id);
    }
}
