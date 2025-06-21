package com.example.happyrunning.ui.fragment;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.blankj.utilcode.util.SPUtils;
import com.example.happyrunning.R;
import com.example.happyrunning.commons.bean.PathRecord;
import com.example.happyrunning.commons.bean.SportMotionRecord;
import com.example.happyrunning.commons.utils.DateUtils;
import com.example.happyrunning.commons.utils.LogUtils;
import com.example.happyrunning.commons.utils.Status_sp;
import com.example.happyrunning.commons.utils.UIhelper;
import com.example.happyrunning.db.DataManager;
import com.example.happyrunning.db.RealmHelper;
import com.example.happyrunning.sport_motion.MotionUtils;
import com.example.happyrunning.ui.activity.SportRecordDetailsActivity;
import com.example.happyrunning.ui.adapter.SportCalendarAdapter;
import com.example.happyrunning.ui.weight.FullyLinearLayoutManager;
import com.example.happyrunning.ui.weight.custom.CustomWeekBar;
import com.haibin.calendarview.Calendar;
import com.haibin.calendarview.CalendarLayout;
import com.haibin.calendarview.CalendarView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import butterknife.BindView;

import static android.app.Activity.RESULT_OK;

/**
 * Home_Fragment：应用首页 Fragment
 * 主要功能：
 *  1. 显示日历，标记有运动记录的日期
 *  2. 选中日期后，列出该日的运动详情列表
 *  3. 跳转到运动详情页
 */
public class Home_Fragment extends Fragment {

    // 顶部标题栏
    private TextView tvTitle;
    // 显示“月-日”
    private TextView mTextMonthDay;
    // 显示“年”
    private TextView mTextYear;
    // 显示农历
    private TextView mTextLunar;
    // 显示当日号数
    private TextView mTextCurrentDay;

    // 日历视图
    private CalendarView mCalendarView;
    // 日历展开/收起布局
    private CalendarLayout mCalendarLayout;
    // “回到今天”图标按钮
    private ImageView imgCurrent;

    // RecyclerView：展示当日 PathRecord 列表
    private RecyclerView mRecyclerView;
    // 当日无运动时隐藏，否则显示
    @BindView(R.id.sport_achievement)
    LinearLayout sport_achievement;

    // 适配器和数据源
    private SportCalendarAdapter adapter;
    private List<PathRecord> sportList = new ArrayList<>();

    // 数据管理：封装了 RealmHelper 对象
    private DataManager dataManager;

    // 当前选中年，用于年视图切换
    private int mYear;

    // 详情跳转请求码
    private final int SPORT = 0x0012;

    // Dialog 占位（若要弹提示框可用）
    private Dialog tipDialog = null;

    /** onCreate 不做 UI 相关操作，留给 onCreateView */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    /**
     * 加载布局 & 绑定视图
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // 1. 绑定视图
        tvTitle          = view.findViewById(R.id.tv_title);
        mTextMonthDay    = view.findViewById(R.id.tv_month_day);
        mTextYear        = view.findViewById(R.id.tv_year);
        mTextLunar       = view.findViewById(R.id.tv_lunar);
        mTextCurrentDay  = view.findViewById(R.id.tv_current_day);
        mCalendarView    = view.findViewById(R.id.calendarView);
        mCalendarLayout  = view.findViewById(R.id.calendarLayout);
        imgCurrent       = view.findViewById(R.id.img_current);
        mRecyclerView    = view.findViewById(R.id.recyclerView);
        sport_achievement = view.findViewById(R.id.sport_achievement);

        // 2. 初始化数据、UI & 日历标记
        initData();

        return view;
    }

    /**
     * 绑定交互监听
     */
    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // 点击“今天”图标，滚动到今天
        imgCurrent.setOnClickListener(v -> mCalendarView.scrollToCurrent());

        // 点击“月-日”标题：展开/收起日历或进入年选择
        mTextMonthDay.setOnClickListener(v -> {
            if (!mCalendarLayout.isExpand()) {
                mCalendarLayout.expand();
            } else {
                mCalendarView.showYearSelectLayout(mYear);
                mTextLunar.setVisibility(View.GONE);
                mTextYear.setVisibility(View.GONE);
                mTextMonthDay.setText(String.valueOf(mYear));
            }
        });

        // 日历选中某天，刷新上方文字 & 列表数据
        mCalendarView.setOnCalendarSelectListener(new CalendarView.OnCalendarSelectListener() {
            @Override
            public void onCalendarOutOfRange(Calendar calendar) { }

            @Override
            public void onCalendarSelect(Calendar calendar, boolean isClick) {
                // 恢复可见性
                mTextLunar.setVisibility(View.VISIBLE);
                mTextYear.setVisibility(View.VISIBLE);

                // 更新“月-日”“年”“农历”
                mTextMonthDay.setText(UIhelper.getString(
                        R.string.date_month_day, calendar.getMonth(), calendar.getDay()));
                mTextYear.setText(String.valueOf(calendar.getYear()));
                mTextLunar.setText(calendar.getLunar());

                // 记录当前年
                mYear = calendar.getYear();

                // 加载该日期的运动路径列表
                String dateTag = DateUtils.formatStringDateShort(
                        calendar.getYear(), calendar.getMonth(), calendar.getDay());
                getSports(dateTag);

                // 日志输出
                LogUtils.d("onDateSelected", calendar.getYear() + "-" +
                        calendar.getMonth() + "-" + calendar.getDay());
            }
        });

        // 年视图切换回调：只更新标题
        mCalendarView.setOnYearChangeListener(year ->
                mTextMonthDay.setText(String.valueOf(year))
        );

        // 列表点击：跳转到运动详情页面
        adapter.setOnItemClickListener((adapter, view, position) ->
                SportRecordDetailsActivity.StartActivity(
                        getActivity(), sportList.get(position)));
    }

    /**
     * 初始化日历 & 列表 & 数据标记
     */
    public void initData() {
        // 创建数据管理器
        dataManager = new DataManager(new RealmHelper());

        // 获取当前年份
        mYear = mCalendarView.getCurYear();
        mTextYear.setText(String.valueOf(mYear));

        // 设置当前“月-日” & “农历”“日”
        mTextMonthDay.setText(UIhelper.getString(
                R.string.date_month_day,
                mCalendarView.getCurMonth(), mCalendarView.getCurDay()));
        mTextLunar.setText("今日");
        mTextCurrentDay.setText(String.valueOf(mCalendarView.getCurDay()));

        // 日历从周日开始
        mCalendarView.setWeekStarWithSun();

        // RecyclerView：垂直但不滚动，全部在 Fragment 内滑动
        mRecyclerView.setLayoutManager(
                new FullyLinearLayoutManager(
                        mRecyclerView, LinearLayoutManager.VERTICAL, false) {
                    @Override
                    public boolean canScrollVertically() {
                        return false;
                    }
                });

        // 为列表 item 添加间距
        int space = getResources().getDimensionPixelSize(R.dimen.line);
        mRecyclerView.addItemDecoration(new SpaceItemDecoration(space));

        // 绑定适配器 & 空数据
        adapter = new SportCalendarAdapter(
                R.layout.adapter_sportcalendar, sportList);
        mRecyclerView.setAdapter(adapter);

        // 自定义周栏样式
        mCalendarView.setWeekBar(CustomWeekBar.class);

        // 首次加载：标记日历 & 填当日列表
        upDateUI();
    }

    /** 更新 UI：打点 & 列表 */
    private void upDateUI() {
        //loadSportData();  // 给日历标记有运动的日期

        // 加载今天的运动记录列表
        String today = DateUtils.formatStringDateShort(
                mCalendarView.getCurYear(),
                mCalendarView.getCurMonth(),
                mCalendarView.getCurDay());
        getSports(today);
    }

    /** 从数据库取全部记录，并在日历上打“记”点 */
    private void loadSportData() {
        try {
            // 查询所有运动记录
            List<SportMotionRecord> records = dataManager.queryRecordList(
                    Integer.parseInt(SPUtils.getInstance().getString(
                            Status_sp.USERID, "0")));

            if (records != null) {
                Map<String, Calendar> schemeMap = new HashMap<>();
                for (SportMotionRecord r : records) {
                    String[] parts = r.getDateTag().split("-");
                    int y = Integer.parseInt(parts[0]);
                    int m = Integer.parseInt(parts[1]);
                    int d = Integer.parseInt(parts[2]);
                    // 构造 Scheme 日历项
                    Calendar scheme = getSchemeCalendar(y, m, d,
                            0xFFCC0000, "记");
                    schemeMap.put(scheme.toString(), scheme);
                }
                // 设置到日历上
                mCalendarView.setSchemeDate(schemeMap);
            }
        } catch (Exception e) {
            LogUtils.e("获取运动数据失败", e);
        }
    }

    /**
     * 构造带标记的 Scheme 日历对象
     */
    private Calendar getSchemeCalendar(
            int year, int month, int day, int color, String text) {
        Calendar calendar = new Calendar();
        calendar.setYear(year);
        calendar.setMonth(month);
        calendar.setDay(day);
        calendar.setSchemeColor(color);
        calendar.setScheme(text);
        calendar.addScheme(new Calendar.Scheme());
        return calendar;
    }

    /**
     * 加载指定日期的运动路径列表并刷新 RecyclerView
     */
    private void getSports(String dateTag) {
        try {
            List<SportMotionRecord> records = dataManager.queryRecordList(
                    Integer.parseInt(SPUtils.getInstance().getString(
                            Status_sp.USERID, "0")), dateTag);

            if (records != null) {
                sportList.clear();
                adapter.notifyDataSetChanged();
                // 转换为 PathRecord 并添加
                for (SportMotionRecord r : records) {
                    PathRecord p = new PathRecord();
                    p.setId(r.getId());
                    p.setmDistance(r.getDistance());
                    p.setmDuration(r.getDuration());
                    p.setmPathLinePoints(
                            MotionUtils.parseLatLngLocations(r.getPathLine()));
                    p.setmStartPoint(
                            MotionUtils.parseLatLngLocation(r.getStratPoint()));
                    p.setmEndPoint(
                            MotionUtils.parseLatLngLocation(r.getEndPoint()));
                    p.setmStartTime(r.getmStartTime());
                    p.setmEndTime(r.getmEndTime());
                    p.setmCalorie(r.getCalorie());
                    p.setmSpeed(r.getSpeed());
                    p.setmDistribution(r.getDistribution());
                    p.setmDateTag(r.getDateTag());
                    sportList.add(p);
                }
                // 根据是否有数据决定提示容器可见性
                sport_achievement.setVisibility(
                        sportList.isEmpty() ? View.GONE : View.VISIBLE);
                adapter.notifyDataSetChanged();
            } else {
                sport_achievement.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            LogUtils.e("获取当日运动数据失败", e);
            sport_achievement.setVisibility(View.GONE);
        }
    }

    /** RecyclerView 每项间距装饰器 */
    protected class SpaceItemDecoration extends RecyclerView.ItemDecoration {
        private int mSpace;
        public SpaceItemDecoration(int space) {
            this.mSpace = space;
        }
        @Override
        public void getItemOffsets(
                Rect outRect, View view,
                RecyclerView parent, RecyclerView.State state) {
            super.getItemOffsets(outRect, view, parent, state);
            outRect.left   = mSpace;
            outRect.right  = mSpace;
            outRect.bottom = mSpace;
            // 第一项也加上上方间距
            outRect.top    = parent.getChildAdapterPosition(view) == 0
                    ? mSpace : 0;
        }
    }

    /**
     * 从详情页返回时，如果有更新，刷新 UI
     */
    @Override
    public void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        if (requestCode == SPORT) {
            upDateUI();
        }
    }
}
