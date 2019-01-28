package com.justtennis.plugin.yout.fragment;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.justtennis.plugin.common.MainActivity;
import com.justtennis.plugin.common.tool.FragmentTool;
import com.justtennis.plugin.fft.R;
import com.justtennis.plugin.fft.databinding.FragmentYoutVideoListBinding;
import com.justtennis.plugin.shared.fragment.AppFragment;
import com.justtennis.plugin.shared.interfaces.interfaces.OnListFragmentInteractionListener;
import com.justtennis.plugin.shared.manager.NotificationManager;
import com.justtennis.plugin.yout.adapter.YoutFindVideoListAdapter;
import com.justtennis.plugin.yout.dto.VideoContent;
import com.justtennis.plugin.yout.dto.VideoDto;
import com.justtennis.plugin.yout.enums.MEDIA_TYPE;
import com.justtennis.plugin.yout.preference.YouTubeSharedPref;
import com.justtennis.plugin.yout.query.response.YoutFindVideoResponse;
import com.justtennis.plugin.yout.task.YoutDownVideoTask;
import com.justtennis.plugin.yout.task.YoutFindVideoTask;
import com.justtennis.plugin.yout.task.YoutGotoUrlTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class YoutFindVideoFragment extends AppFragment {

    private static final String TAG = YoutFindVideoFragment.class.getName();

    private static final String ARG_LIST_DTO = "ARG_LIST_DTO";

    private FragmentYoutVideoListBinding binding;
    private AutoCompleteTextView textView;
    private YoutFindVideoListAdapter publicationListAdapter;
    private List<VideoDto> listDto = new ArrayList<>();
    private List<VideoDto> listDtoArg;
    private YoutDownVideoTask youtDownVideoTask;
    private AsyncTask<Void, String, YoutFindVideoResponse> youtFindVideoTask;
    private FragmentActivity activity;
    private LinearLayout llContent;
    private int countCheckedRow;

    public static Fragment newInstance() {
        return new YoutFindVideoFragment();
    }

    public static Fragment newInstance(ArrayList<VideoDto> listDto) {
        YoutFindVideoFragment fragment = new YoutFindVideoFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_LIST_DTO, listDto);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        activity = getActivity();
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_yout_video_list, container, false);
        textView = binding.publicationMessage;
        llContent = binding.llContent;
        binding.setLoading(false);

        textView.setOnEditorActionListener((v, actionId, event) -> {
            if  ((actionId == EditorInfo.IME_ACTION_DONE)) {
                onClickFab(null);
            }
            return false;
        });

        Bundle arguments = (savedInstanceState != null) ? savedInstanceState : getArguments();
        if (arguments != null) {
            listDtoArg = (ArrayList<VideoDto>) arguments.getSerializable(ARG_LIST_DTO);
            if (listDtoArg != null) {
                listDto.addAll(listDtoArg);
            }
        }

        initializePublicationMessage();
        initializePublicationList();
        initializeFabValidate();

        return binding.getRoot();
    }

    private void initializePublicationList() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            publicationListAdapter = new YoutFindVideoListAdapter(this::updPublicationMessage, item -> onLongClickRow((VideoDto)item), item -> onCheckRow((VideoDto)item), item -> onCheckLongClick((VideoDto)item));
        } else {
            publicationListAdapter = new YoutFindVideoListAdapter((OnListFragmentInteractionListener) item -> updPublicationMessage((VideoDto)item), item -> onLongClickRow((VideoDto)item), item -> onCheckRow((VideoDto)item), item -> onCheckLongClick((VideoDto)item));
        }
        publicationListAdapter.setList(listDto);
        binding.publicationList.setAdapter(publicationListAdapter);
        binding.publicationList.setNestedScrollingEnabled(false);
    }

    private void initializePublicationMessage() {
        AutoCompleteTextView textView = binding.publicationMessage;
        textView.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Nothing here
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updButtonStat();
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Nothing  here
            }
        });

        binding.searchBar.setVisibility((listDtoArg != null && !listDtoArg.isEmpty()) ? View.GONE : View.VISIBLE);
    }

    private void updPublicationMessage(VideoDto dto) {
        if (dto.type == MEDIA_TYPE.PLAYLIST) {
            executeGotoUrl(getContext(), dto);
        }
        else if (dto.type == MEDIA_TYPE.VIDEO) {
            if (!publicationListAdapter.isShowCheck()) {
                boolean playYoutube = true;
                if (dto.downloadPath == null || dto.downloadPath.isEmpty()) {
                    dto.downloadPath = YouTubeSharedPref.getVideoPath(activity, dto.id);
                }
                if (dto.downloadPath != null && !dto.downloadPath.isEmpty()) {
                    File file = new File(dto.downloadPath);
                    if (file.exists()) {
                        playYoutube = false;
                        watchPlayerVideo(activity, file);
                    }
                }
                if (playYoutube) {
                    watchYoutubeVideo(activity, dto.id);
                }
            } else {
                dto.viewHolder.check();
            }
        } else {
            Toast.makeText(activity, dto.url, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean onLongClickRow(VideoDto dto) {
        publicationListAdapter.setShowCheck(!publicationListAdapter.isShowCheck());
        if (publicationListAdapter.isShowCheck()) {
            dto.checked = MEDIA_TYPE.VIDEO == dto.type;
            if (dto.checked) {
                countCheckedRow++;
                updButtonStat();
            }
        } else {
            for(VideoDto d : listDto) {
                d.checked = false;
            }
            countCheckedRow=0;
            updButtonStat();
        }
        publicationListAdapter.notifyDataSetChanged();
        return true;
    }

    private boolean onCheckRow(VideoDto dto) {
        countCheckedRow += dto.checked ? 1 : -1;
        updButtonStat();
        return true;
    }

    private boolean onCheckLongClick(VideoDto dto) {
        for(VideoDto d : listDto) {
            d.checked = dto.checked;
        }
        countCheckedRow = dto.checked ? listDto.size() : 0;
        updButtonStat();
        publicationListAdapter.notifyDataSetChanged();
        return true;
    }

    public static void watchPlayerVideo(Context context, File file){
        Intent viewIntent = new Intent(Intent.ACTION_VIEW);
        viewIntent.setDataAndType(Uri.fromFile(file), "audio/*");
        context.startActivity(Intent.createChooser(viewIntent, null));
    }

    public static void watchYoutubeVideo(Context context, String id){
        Intent appIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:" + id));
        Intent webIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("http://www.youtube.com/watch?v=" + id));
        try {
            context.startActivity(appIntent);
        } catch (ActivityNotFoundException ex) {
            context.startActivity(webIntent);
        }
    }

    private void updButtonStat() {
        boolean check = false;
        if (publicationListAdapter.isShowCheck()) {
            check = countCheckedRow > 0;
        } else {
            check = youtFindVideoTask == null && textView.getText().length() > 0;
        }
        FragmentTool.enableFab(activity, check);
    }

    private void initializeFabValidate() {
        FragmentTool.initializeFabDrawable(Objects.requireNonNull(activity), FragmentTool.INIT_FAB_IMAGE.VALIDATE);
        FragmentTool.onClickFab(activity, this::onClickFab);
        updButtonStat();
    }

    private void closeKeyboard() {
        InputMethodManager imm =  (InputMethodManager) Objects.requireNonNull(getContext()).getSystemService(Context.INPUT_METHOD_SERVICE);
        assert imm != null;
        imm.hideSoftInputFromWindow(llContent.getWindowToken(), 0);
    }

    private void onClickFab(View view) {
        closeKeyboard();
        final Context context = getContext();
        if (publicationListAdapter.isShowCheck()) {
            executeDownVideo(context);
        } else {
            executeFindVideo(context);
        }
    }

    private void executeGotoUrl(Context context, VideoDto dto) {
        AsyncTask<Void, String, YoutFindVideoResponse> youtGotoUrlTask = new YoutGotoUrlTask(context, dto.url) {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                binding.setLoading(true);
            }

            @Override
            protected void onPostExecute(YoutFindVideoResponse response) {
                super.onPostExecute(response);
                if (response != null && !response.videoList.isEmpty()) {
                    ArrayList<VideoDto> list = new ArrayList<>();
                    list.addAll(VideoContent.toDto(response.videoList));

                    FragmentTool.replaceFragment(activity, YoutFindVideoFragment.newInstance(list));
                }
                binding.setLoading(false);
            }

            @Override
            protected void onProgressUpdate(String... values) {
                super.onProgressUpdate(values);
                NotificationManager.onTaskProcessUpdate(activity, values);
            }
        }.execute();
    }

    private void executeDownVideo(Context context) {
        if (youtDownVideoTask != null) {
            return;
        }
        ((MainActivity)activity).showMessage("Starting Download to MP3.");
        youtDownVideoTask = new YoutDownVideoTask(context, listDto) {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                for(VideoDto dto : listDto) {
                    if (dto.checked) {
                        dto.updateDownloadStatus(VideoDto.STATUS_DOWNLOAD.PENDING);
                    }
                }
            }

            @Override
            protected void onPostExecute(List<VideoDto> list) {
                super.onPostExecute(list);
                for(VideoDto dto : list) {
                    dto.updateDownloadStatus(dto.downloadStatus);
                    if (dto.downloadPath != null && !dto.downloadPath.isEmpty()) {
                        YouTubeSharedPref.setVideoPath(activity, dto.id, dto.downloadPath);
                    }
                }
                publicationListAdapter.notifyDataSetChanged();
                youtDownVideoTask = null;
            }

            @Override
            protected void onProgressUpdate(String... values) {
                super.onProgressUpdate(values);
                NotificationManager.onTaskProcessUpdate(activity, values);
            }
        };
        youtDownVideoTask.execute();
    }

    private void executeFindVideo(Context context) {
        if (youtFindVideoTask != null) {
            return;
        }
        youtFindVideoTask = new YoutFindVideoTask(context, textView.getText().toString()) {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                binding.setLoading(true);
                listDto.clear();
                publicationListAdapter.setShowCheck(false);
            }

            @Override
            protected void onPostExecute(YoutFindVideoResponse response) {
                super.onPostExecute(response);
                if (response != null && !response.videoList.isEmpty()) {
                    listDto.addAll(VideoContent.toDto(response.videoList));
                }
                publicationListAdapter.notifyDataSetChanged();
                binding.setLoading(false);
                youtFindVideoTask = null;
            }

            @Override
            protected void onProgressUpdate(String... values) {
                super.onProgressUpdate(values);
                NotificationManager.onTaskProcessUpdate(activity, values);
            }
        }.execute();
    }
}
