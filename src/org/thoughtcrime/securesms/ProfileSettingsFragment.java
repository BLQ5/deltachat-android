package org.thoughtcrime.securesms;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import com.b44t.messenger.DcChat;
import com.b44t.messenger.DcChatlist;
import com.b44t.messenger.DcContact;
import com.b44t.messenger.DcContext;
import com.b44t.messenger.DcEventCenter;

import org.thoughtcrime.securesms.connect.ApplicationDcContext;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.util.StickyHeaderDecoration;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.Locale;

public class ProfileSettingsFragment extends Fragment
             implements ProfileSettingsAdapter.ItemClickListener, DcEventCenter.DcEventDelegate {

  public static final String LOCALE_EXTRA  = "locale_extra";
  public static final String CHAT_ID_EXTRA = "chat_id";
  public static final String CONTACT_ID_EXTRA = "contact_id";

  private RecyclerView           recyclerView;
  private ProfileSettingsAdapter adapter;

  private Locale               locale;
  private ApplicationDcContext dcContext;
  protected int                chatId;
  private int                  contactId;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);

    locale = (Locale)getArguments().getSerializable(LOCALE_EXTRA);
    if (locale == null) throw new AssertionError();
    chatId = getArguments().getInt(CHAT_ID_EXTRA, -1);
    contactId = getArguments().getInt(CONTACT_ID_EXTRA, -1);
    dcContext = DcHelper.getContext(getContext());
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.profile_settings_fragment, container, false);
    adapter = new ProfileSettingsAdapter(getContext(), GlideApp.with(this), locale,this);

    recyclerView  = ViewUtil.findById(view, R.id.recycler_view);
    recyclerView.setAdapter(adapter);
    recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
    recyclerView.addItemDecoration(new StickyHeaderDecoration(adapter, false, true));

    update();

    dcContext.eventCenter.addObserver(DcContext.DC_EVENT_CHAT_MODIFIED, this);
    dcContext.eventCenter.addObserver(DcContext.DC_EVENT_CONTACTS_CHANGED, this);
    dcContext.eventCenter.addObserver(DcContext.DC_EVENT_MSGS_CHANGED, this);
    dcContext.eventCenter.addObserver(DcContext.DC_EVENT_INCOMING_MSG, this);
    return view;
  }

  @Override
  public void onDestroyView() {
    dcContext.eventCenter.removeObservers(this);
    super.onDestroyView();
  }

  @Override
  public void handleEvent(int eventId, Object data1, Object data2) {
    update();
  }

  private void update()
  {
    int[]      memberList = null;
    DcChatlist sharedChats = null;

    DcChat dcChat = null;
    DcContact dcContact = null;
    if (contactId>0) { dcContact = dcContext.getContact(contactId); }
    if (chatId>0)    { dcChat    = dcContext.getChat(chatId); }

    if(dcChat!=null && dcChat.isGroup()) {
      memberList = dcContext.getChatContacts(chatId);
    }
    else if(contactId>0) {
      sharedChats = dcContext.getChatlist(0, null, contactId);
    }

    adapter.changeData(memberList, dcContact, sharedChats, dcChat);
  }

  @Override
  public void onSettingsClicked(int settingsId) {
    switch(settingsId) {
      case ProfileSettingsAdapter.SETTING_CONTACT_ADDR:
        onContactAddrClicked();
        break;
      case ProfileSettingsAdapter.SETTING_CONTACT_NAME:
        onEditContactName();
        break;
      case ProfileSettingsAdapter.SETTING_ENCRYPTION:
        onEncrInfo();
        break;
      case ProfileSettingsAdapter.SETTING_NEW_CHAT:
        onNewChat();
        break;
      case ProfileSettingsAdapter.SETTING_BLOCK_CONTACT:
        onBlockContact();
        break;
    }
  }

  private void onContactAddrClicked() {
    String address = dcContext.getContact(contactId).getAddr();
    new AlertDialog.Builder(getContext())
        .setTitle(address)
        .setItems(new CharSequence[]{
                getContext().getString(R.string.menu_copy_to_clipboard)
            },
            (dialogInterface, i) -> {
              Util.writeTextToClipboard(getContext(), address);
              Toast.makeText(getContext(), getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show();
            })
        .setNegativeButton(R.string.cancel, null)
        .show();
  }

  private void onEncrInfo() {
    String info_str = dcContext.getContactEncrInfo(contactId);
    new AlertDialog.Builder(getActivity())
        .setMessage(info_str)
        .setPositiveButton(android.R.string.ok, null)
        .show();
  }

  private void onEditContactName() {
    DcContact dcContact = dcContext.getContact(contactId);
    final EditText txt = new EditText(getActivity());
    txt.setText(dcContact.getName());
    new AlertDialog.Builder(getActivity())
        .setTitle(R.string.menu_edit_name)
        .setView(txt)
        .setPositiveButton(android.R.string.ok, (dialog, whichButton) -> {
          String newName = txt.getText().toString();
          dcContext.createContact(newName, dcContact.getAddr());
        })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void onNewChat() {
    DcContact dcContact = dcContext.getContact(contactId);
    new AlertDialog.Builder(getActivity())
        .setMessage(getActivity().getString(R.string.ask_start_chat_with, dcContact.getNameNAddr()))
        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
          int chatId = dcContext.createChatByContactId(dcContact.getId());
          if (chatId != 0) {
            Intent intent = new Intent(getActivity(), ConversationActivity.class);
            intent.putExtra(ConversationActivity.CHAT_ID_EXTRA, chatId);
            getActivity().startActivity(intent);
            getActivity().finish();
          }
        })
        .setNegativeButton(R.string.cancel, null)
        .show();
  }

  private void onBlockContact() {
    DcContact dcContact = dcContext.getContact(contactId);
    if(dcContact.isBlocked()) {
      new AlertDialog.Builder(getActivity())
          .setMessage(R.string.ask_unblock_contact)
          .setCancelable(true)
          .setNegativeButton(android.R.string.cancel, null)
          .setPositiveButton(R.string.menu_unblock_contact, (dialog, which) -> {
            dcContext.blockContact(contactId, 0);
          }).show();
    }
    else {
      new AlertDialog.Builder(getActivity())
          .setMessage(R.string.ask_block_contact)
          .setCancelable(true)
          .setNegativeButton(android.R.string.cancel, null)
          .setPositiveButton(R.string.menu_block_contact, (dialog, which) -> {
            dcContext.blockContact(contactId, 1);
          }).show();
    }
  }
}
