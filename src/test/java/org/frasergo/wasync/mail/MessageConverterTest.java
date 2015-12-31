package org.frasergo.wasync.mail;

import android.database.MatrixCursor;
import android.content.ContentValues;
import android.provider.CallLog;
import com.fsck.k9.mail.Address;
import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.internet.BinaryTempFileBody;
import com.fsck.k9.mail.internet.MimeMessage;
import org.frasergo.wasync.SmsConsts;
import org.frasergo.wasync.contacts.ContactAccessor;
import org.frasergo.wasync.preferences.AddressStyle;
import org.frasergo.wasync.preferences.MarkAsReadTypes;
import org.frasergo.wasync.preferences.Preferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
public class MessageConverterTest {
    private MessageConverter messageConverter;
    @Mock private Preferences preferences;
    @Mock private PersonLookup personLookup;
    @Mock private ContactAccessor contactAccessor;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        BinaryTempFileBody.setTempDirectory(Robolectric.application.getCacheDir());
        messageConverter = new MessageConverter(Robolectric.application,
                preferences, "foo@example.com", personLookup, contactAccessor);
    }

    @Test(expected = MessagingException.class)
    public void testMessageToContentValuesWithNullMessageThrowsMessagingException() throws Exception {
        messageConverter.messageToContentValues(null);
    }

    @Test
    public void testMessageToContentValuesWithUnknownMessageTypeGetParsedAsSMS() throws Exception {
        final String message = "Subject: Call with +12121\n" +
                "From: +12121 <+12121@unknown.email>\n" +
                "To: test@example.com\n" +
                "MIME-Version: 1.0\n" +
                "Content-Type: text/plain;\n" +
                " charset=utf-8\n" +
                "References: <3j20u1wmbcyik9lw0yaf8bfc.+12121@sms-backup-plus.local>\n" +
                "Message-ID: <5c0e190205376da44656936fd7d9900c@sms-backup-plus.local>\n" +
                "X-wasync-datatype: INVALID\n" +
                "Content-Transfer-Encoding: quoted-printable\n" +
                "\n" +
                "Some Text";

        final MimeMessage mimeMessage = new MimeMessage(new ByteArrayInputStream(message.getBytes()), true);
        final ContentValues values = messageConverter.messageToContentValues(mimeMessage);
        assertThat(values.getAsString(SmsConsts.ADDRESS)).isNull();
    }

    @Test public void testMessageToContentValuesWithSMS() throws Exception {
        final ContentValues values = messageConverter.messageToContentValues(createSMSMessage());
        assertThat(values.getAsString(SmsConsts.ADDRESS)).isEqualTo("+121332");
        assertThat(values.getAsString(SmsConsts.TYPE)).isEqualTo("2");
        assertThat(values.getAsString(SmsConsts.PROTOCOL)).isNull();
        assertThat(values.getAsString(SmsConsts.SERVICE_CENTER)).isNull();
        assertThat(values.getAsString(SmsConsts.DATE)).isEqualTo("1420759456762");
        assertThat(values.getAsString(SmsConsts.STATUS)).isEqualTo("-1");
        assertThat(values.getAsString(SmsConsts.THREAD_ID)).isNull();
        assertThat(values.getAsString(SmsConsts.READ)).isEqualTo("1");
        assertThat(values.getAsString(SmsConsts.BODY)).isEqualTo("DasßAsß");
    }


    @Test public void testMessageToContentValuesWithCalllog() throws Exception {
        PersonRecord record = new PersonRecord(1, "The name", "email@foo.com", "+1234");
        when(personLookup.lookupPerson("+12121")).thenReturn(record);
        final ContentValues values = messageConverter.messageToContentValues(createCallLogMessage());
        assertThat(values.getAsString(CallLog.Calls.NUMBER)).isEqualTo("+12121");
        assertThat(values.getAsString(CallLog.Calls.TYPE)).isEqualTo("3");
        assertThat(values.getAsString(CallLog.Calls.DATE)).isEqualTo("1419163218194");
        assertThat(values.getAsLong(CallLog.Calls.DURATION)).isEqualTo(44L);
        assertThat(values.getAsInteger(CallLog.Calls.NEW)).isEqualTo(0);
        assertThat(values.getAsString(CallLog.Calls.CACHED_NAME)).isEqualTo("The name");
        assertThat(values.getAsInteger(CallLog.Calls.CACHED_NUMBER_TYPE)).isEqualTo(-2);
    }

    @Test public void testMessageToContentValuesWithCalllogFromUnknownPerson() throws Exception {
        PersonRecord record = new PersonRecord(-1, null, null, null);
        when(personLookup.lookupPerson("+12121")).thenReturn(record);
        final ContentValues values = messageConverter.messageToContentValues(createCallLogMessage());
        assertThat(values.containsKey(CallLog.Calls.CACHED_NAME)).isFalse();
        assertThat(values.containsKey(CallLog.Calls.CACHED_NUMBER_TYPE)).isFalse();
    }

    @Test public void testConvertMessagesSeenFlagFromMessageStatusWithSMS() throws Exception {
        MatrixCursor cursor = new MatrixCursor(new String[] {SmsConsts.ADDRESS, SmsConsts.READ} );
        cursor.addRow(new Object[]{ "foo", "0" });
        cursor.addRow(new Object[]{ "foo", "1" });
        cursor.moveToFirst();

        PersonRecord record = mock(PersonRecord.class);
        when(personLookup.lookupPerson(any(String.class))).thenReturn(record);
        when(record.getAddress(any(AddressStyle.class))).thenReturn(new Address("foo"));
        when(preferences.getMarkAsReadType()).thenReturn(MarkAsReadTypes.MESSAGE_STATUS);

        messageConverter = new MessageConverter(Robolectric.application,
                preferences, "foo@example.com", personLookup, contactAccessor);

        ConversionResult res = messageConverter.convertMessages(cursor, DataType.SMS);
        assertThat(res.getMessages().get(0).isSet(Flag.SEEN)).isFalse();

        cursor.moveToNext();
        res = messageConverter.convertMessages(cursor, DataType.SMS);
        assertThat(res.getMessages().get(0).isSet(Flag.SEEN)).isTrue();
    }

    @Test public void testConvertMessagesSeenFlagUnreadWithSMS() throws Exception {
        MatrixCursor cursor = new MatrixCursor(new String[] {SmsConsts.ADDRESS, SmsConsts.READ} );
        cursor.addRow(new Object[]{ "foo", "0" });
        cursor.addRow(new Object[]{ "foo", "1" });
        cursor.moveToFirst();

        PersonRecord record = mock(PersonRecord.class);
        when(personLookup.lookupPerson(any(String.class))).thenReturn(record);
        when(record.getAddress(any(AddressStyle.class))).thenReturn(new Address("foo"));
        when(preferences.getMarkAsReadType()).thenReturn(MarkAsReadTypes.UNREAD);

        messageConverter = new MessageConverter(Robolectric.application,
                preferences, "foo@example.com", personLookup, contactAccessor);

        ConversionResult res = messageConverter.convertMessages(cursor, DataType.SMS);
        assertThat(res.getMessages().get(0).isSet(Flag.SEEN)).isFalse();

        cursor.moveToNext();
        res = messageConverter.convertMessages(cursor, DataType.SMS);
        assertThat(res.getMessages().get(0).isSet(Flag.SEEN)).isFalse();
    }

    @Test public void testConvertMessagesSeenFlagReadWithSMS() throws Exception {
        MatrixCursor cursor = new MatrixCursor(new String[] {SmsConsts.ADDRESS, SmsConsts.READ} );
        cursor.addRow(new Object[]{ "foo", "0" });
        cursor.addRow(new Object[]{ "foo", "1" });
        cursor.moveToFirst();

        PersonRecord record = mock(PersonRecord.class);
        when(personLookup.lookupPerson(any(String.class))).thenReturn(record);
        when(record.getAddress(any(AddressStyle.class))).thenReturn(new Address("foo"));
        when(preferences.getMarkAsReadType()).thenReturn(MarkAsReadTypes.READ);

        messageConverter = new MessageConverter(Robolectric.application,
                preferences, "foo@example.com", personLookup, contactAccessor);

        ConversionResult res = messageConverter.convertMessages(cursor, DataType.SMS);
        assertThat(res.getMessages().get(0).isSet(Flag.SEEN)).isTrue();

        cursor.moveToNext();
        res = messageConverter.convertMessages(cursor, DataType.SMS);
        assertThat(res.getMessages().get(0).isSet(Flag.SEEN)).isTrue();
    }

    private MimeMessage createSMSMessage() throws IOException, MessagingException {
        final String message = "Subject: SMS with +121332\n" +
                "MIME-Version: 1.0\n" +
                "Content-Type: text/plain;\n" +
                " charset=utf-8\n" +
                "To: +121332 <+121332@unknown.email>\n" +
                "From: foo@test.com\n" +
                "References: <3j20u1wmbcyik9lw0yaf8bfc.+121332@sms-backup-plus.local>\n" +
                "Message-ID: <215765a03863b133d3b64d114a501251@sms-backup-plus.local>\n" +
                "X-wasync-address: +121332\n" +
                "X-wasync-datatype: SMS\n" +
                "X-wasync-backup-time: 8 Jan 2015 23:26:20 GMT\n" +
                "X-wasync-version: 1549\n" +
                "Date: Thu, 08 Jan 2015 18:24:16 -0500\n" +
                "X-wasync-id: 12\n" +
                "X-wasync-type: 2\n" +
                "X-wasync-date: 1420759456762\n" +
                "X-wasync-thread: 23\n" +
                "X-wasync-read: 1\n" +
                "X-wasync-status: -1\n" +
                "Content-Transfer-Encoding: quoted-printable\n" +
                "\n" +
                "Das=C3=9FAs=C3=9F";

        return new MimeMessage(new ByteArrayInputStream(message.getBytes()), true);
    }

    private MimeMessage createCallLogMessage() throws IOException, MessagingException {
        final String message = "Subject: Call with +12121\n" +
                "From: +12121 <+12121@unknown.email>\n" +
                "To: test@example.com\n" +
                "MIME-Version: 1.0\n" +
                "Content-Type: text/plain;\n" +
                " charset=utf-8\n" +
                "References: <3j20u1wmbcyik9lw0yaf8bfc.+12121@sms-backup-plus.local>\n" +
                "Message-ID: <5c0e190205376da44656936fd7d9900c@sms-backup-plus.local>\n" +
                "X-wasync-address: +12121\n" +
                "X-wasync-datatype: CALLLOG\n" +
                "X-wasync-backup-time: 7 Jan 2015 01:12:04 GMT\n" +
                "X-wasync-version: 1548\n" +
                "Date: Sun, 21 Dec 2014 07:00:18 -0500\n" +
                "X-wasync-id: 1\n" +
                "X-wasync-type: 3\n" +
                "X-wasync-date: 1419163218194\n" +
                "X-wasync-duration: 44\n" +
                "Content-Transfer-Encoding: quoted-printable\n" +
                "\n" +
                "+12121 (missed call)";

        return new MimeMessage(new ByteArrayInputStream(message.getBytes()), true);
    }
}
