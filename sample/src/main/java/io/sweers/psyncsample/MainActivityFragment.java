package io.sweers.psyncsample;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * A placeholder fragment containing a simple view.
 */
public class MainActivityFragment extends Fragment {

  private static final String NEWLINE = "\n";
  private static final String TAB = "\t";

  public MainActivityFragment() {
  }

  @Override public View onCreateView(LayoutInflater inflater,
      ViewGroup container,
      Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.fragment_main, container, false);

    // Normally you would do this in your Application class
    P.init(getActivity().getApplication());

    TextView display = view.findViewById(R.id.display);

    //noinspection StringBufferReplaceableByString
    String text = new StringBuilder().append("Server category:")
        .append(NEWLINE)
        .append(TAB)
        .append("key: ")
        .append(P.CategoryServer.KEY)
        .append(NEWLINE)
        .append(NEWLINE)
        .append("Number of columns:")
        .append(NEWLINE)
        .append(TAB)
        .append("key: ")
        .append(P.NumberOfColumns.KEY)
        .append(NEWLINE)
        .append(TAB)
        .append("defaultValue: ")
        .append(P.NumberOfColumns.defaultValue())
        .append(NEWLINE)
        .append(NEWLINE)
        .append("Number of rows:")
        .append(NEWLINE)
        .append(TAB)
        .append("key: ")
        .append(P.NumberOfRows.KEY)
        .append(NEWLINE)
        .append(TAB)
        .append("defaultResId: ")
        .append(P.NumberOfRows.DEFAULT_RES_ID)
        .append(NEWLINE)
        .append(TAB)
        .append("defaultValue: ")
        .append(P.NumberOfRows.defaultValue())
        .append(NEWLINE)
        .append(NEWLINE)
        .append("Primary color:")
        .append(NEWLINE)
        .append(TAB)
        .append("key: ")
        .append(P.PrimaryColor.KEY)
        .append(NEWLINE)
        .append(TAB)
        .append("defaultResId: ")
        .append(P.PrimaryColor.DEFAULT_RES_ID)
        .append(NEWLINE)
        .append(TAB)
        .append("defaultValue: ")
        .append(P.PrimaryColor.defaultValue())
        .append(NEWLINE)
        .append(NEWLINE)
        .append("Request agent:")
        .append(NEWLINE)
        .append(TAB)
        .append("key: ")
        .append(P.RequestAgent.KEY)
        .append(NEWLINE)
        .append(TAB)
        .append("defaultValue: ")
        .append(P.RequestAgent.defaultValue())
        .append(NEWLINE)
        .append(NEWLINE)
        .append("Request types:")
        .append(NEWLINE)
        .append(TAB)
        .append("key: ")
        .append(P.RequestTypes.KEY)
        .append(NEWLINE)
        .append(TAB)
        .append("defaultResId: ")
        .append(P.RequestTypes.DEFAULT_RES_ID)
        .append(NEWLINE)
        .append(TAB)
        .append("defaultValue: ")
        .append(P.RequestTypes.defaultValue())
        .append(NEWLINE)
        .append(NEWLINE)
        .append("Server url:")
        .append(NEWLINE)
        .append(TAB)
        .append("key: ")
        .append(P.ServerUrl.KEY)
        .append(NEWLINE)
        .append(TAB)
        .append("defaultResId: ")
        .append(P.ServerUrl.DEFAULT_RES_ID)
        .append(NEWLINE)
        .append(TAB)
        .append("defaultValue: ")
        .append(P.ServerUrl.defaultValue())
        .append(NEWLINE)
        .append(NEWLINE)
        .append("Show images:")
        .append(NEWLINE)
        .append(TAB)
        .append("key: ")
        .append(P.ShowImages.KEY)
        .append(NEWLINE)
        .append(TAB)
        .append("defaultResId: ")
        .append(P.ShowImages.defaultValue())
        .append(NEWLINE)
        .append(NEWLINE)
        .append("Use inputs:")
        .append(NEWLINE)
        .append(TAB)
        .append("key: ")
        .append(P.UseInputs.KEY)
        .append(NEWLINE)
        .append(TAB)
        .append("defaultResId: ")
        .append(P.UseInputs.DEFAULT_RES_ID)
        .append(NEWLINE)
        .append(TAB)
        .append("defaultValue: ")
        .append(P.UseInputs.defaultValue())
        .append(NEWLINE)
        .append(NEWLINE)
        .toString();

    display.setVisibility(View.VISIBLE);
    display.setText(text);
    return view;
  }
}
