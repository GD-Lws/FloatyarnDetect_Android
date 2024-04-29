package com.example.myapplication;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbDevice;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.HexDump;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SerialTestActivity extends Activity implements View.OnClickListener {
    private UsbManager usbManager = null;
    private Button bt_open,bt_send,bt_refresh;
    private EditText et_send;
    private TextView tv_rec;
    private ListView lv_device;
    private ListItem device_select = null;
    private static final int WRITE_WAIT_MILLIS = 2000;
    private static final int READ_WAIT_MILLIS = 2000;
    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    private static final String TAG = "IOSerial";
    private final ArrayList<ListItem> listItems = new ArrayList<>();
    private ArrayAdapter<ListItem> listAdapter;
    private int baudRate = 19200;
    private boolean serialOpenFlag = false;
    private UsbSerialPort usbSerialPort;
    private UsbDeviceConnection usbConnection;
    private enum UsbPermission { Unknown, Requested, Granted, Denied }
    private UsbPermission usbPermission = UsbPermission.Unknown;
    private SerialInputOutputManager usbIoManager;

    static class ListItem {
        UsbDevice device;
        int port;
        UsbSerialDriver driver;

        ListItem(UsbDevice device, int port, UsbSerialDriver driver) {
            this.device = device;
            this.port = port;
            this.driver = driver;
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_serial_test);
//        initView();

        listAdapter = new ArrayAdapter<ListItem>(SerialTestActivity.this, 0, listItems) {
            @NonNull
            @Override
            public View getView(int position, View view, @NonNull ViewGroup parent) {
                ListItem item = listItems.get(position);
                if (view == null)
                    view = SerialTestActivity.this.getLayoutInflater().inflate(R.layout.device_list_item, parent, false);
                TextView text1 = view.findViewById(R.id.text1);
                TextView text2 = view.findViewById(R.id.text2);
                if(item.driver == null)
                    text1.setText("<no driver>");
                else if(item.driver.getPorts().size() == 1)
                    text1.setText(item.driver.getClass().getSimpleName().replace("SerialDriver",""));
                else
                    text1.setText(item.driver.getClass().getSimpleName().replace("SerialDriver","")+", Port "+item.port);
                text2.setText(String.format(Locale.US, "Vendor %04X, Product %04X", item.device.getVendorId(), item.device.getProductId()));
                return view;
            }
        };
        lv_device.setAdapter(listAdapter);
        lv_device.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ListItem item = listItems.get(position);
                if(item.driver == null) {
                    Toast.makeText(SerialTestActivity.this, "no driver", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(SerialTestActivity.this, "Select driverId" + item.device.getDeviceId() + "port" +  item.port + "baud" + baudRate, Toast.LENGTH_SHORT).show();
                    device_select = item;
                }
            }
        });
    }

//    private void initView(){
//        bt_open = findViewById(R.id.bt_open);
//        bt_send = findViewById(R.id.bt_send);
//        bt_refresh = findViewById(R.id.bt_refresh);
//        et_send = findViewById(R.id.et_send);
//
//        tv_rec = findViewById(R.id.tv_rec);
//        lv_device = findViewById(R.id.lv_derive);
//
//        bt_refresh.setOnClickListener(this);
//        bt_open.setOnClickListener(this);
//        bt_send.setOnClickListener(this);
//    }

    private void refresh() {
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        UsbSerialProber usbDefaultProber = UsbSerialProber.getDefaultProber();
        listItems.clear();
        for(UsbDevice device : usbManager.getDeviceList().values()) {
            UsbSerialDriver driver = usbDefaultProber.probeDevice(device);
            if(driver != null) {
                for(int port = 0; port < driver.getPorts().size(); port++)
                    listItems.add(new ListItem(device, port, driver));
            } else {
                listItems.add(new ListItem(device, 0, null));
            }
        }
        listAdapter.notifyDataSetChanged();
    }

    private void requestUsbPermission(UsbDevice usbDevice) {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (usbManager != null) {
            PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(INTENT_ACTION_GRANT_USB), PendingIntent.FLAG_IMMUTABLE);
            usbManager.requestPermission(usbDevice, permissionIntent);
        }
    }

    private void disconnect() {
        serialOpenFlag = false;
        if(usbIoManager != null) {
            usbIoManager.setListener(null);
            usbIoManager.stop();
        }
        if(usbManager != null) {
            usbManager = null;
        }
        try {
            usbSerialPort.close();
        } catch (IOException ignored) {}
        usbSerialPort = null;
        if (usbConnection != null){
            usbConnection.close();
            usbConnection = null;
        }
        listItems.clear();
    }

    private void openSerial(){
        if (device_select != null)
        {
            if (usbManager == null){
                usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            }
            usbConnection = usbManager.openDevice(device_select.driver.getDevice());
            if(usbConnection == null) {
                Toast.makeText(SerialTestActivity.this, "UsbConnection empty!", Toast.LENGTH_SHORT).show();
                int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_MUTABLE : 0;
                Intent intent = new Intent(INTENT_ACTION_GRANT_USB);
                intent.setPackage(SerialTestActivity.this.getPackageName());
                PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(SerialTestActivity.this, 0, intent, flags);
                usbManager.requestPermission(device_select.driver.getDevice(), usbPermissionIntent);
                return;
            }
            usbSerialPort = device_select.driver.getPorts().get(device_select.port); // Most devices have just one port (port 0)
            try {
                usbSerialPort.open(usbConnection);
                usbSerialPort.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            } catch (IOException e) {
                Toast.makeText(SerialTestActivity.this, "Device serial open failed!", Toast.LENGTH_SHORT).show();
                throw new RuntimeException(e);
            }
            startReceiveData();
            Toast.makeText(SerialTestActivity.this, "Device serial open success!", Toast.LENGTH_SHORT).show();
            serialOpenFlag = true;
        }else {
            Toast.makeText(SerialTestActivity.this, "No Select device", Toast.LENGTH_SHORT).show();
        }
    }

    private void read() {
        if(!serialOpenFlag) {
            Toast.makeText(SerialTestActivity.this, "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            byte[] buffer = new byte[8192];
            int len = usbSerialPort.read(buffer, READ_WAIT_MILLIS);
            receive(Arrays.copyOf(buffer, len));
        } catch (IOException e) {
            // when using read with timeout, USB bulkTransfer returns -1 on timeout _and_ errors
            // like connection loss, so there is typically no exception thrown here on error
//            status("connection lost: " + e.getMessage());
            disconnect();
        }
    }

    public void startReceiveData(){
        if(usbSerialPort == null || !usbSerialPort.isOpen()){return;}
        usbIoManager = new SerialInputOutputManager(usbSerialPort, new SerialInputOutputManager.Listener() {
            @Override
            public void onNewData(byte[] data) {
                // 在这里处理接收到的 usb 数据
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {tv_rec.append(new String(data));
                    }
                });
            }
            @Override
            public void onRunError(Exception e) {
                Log.e(TAG, "usb 断开了" );
                disconnect();
                e.printStackTrace();
            }
        });
        usbIoManager.start();
    }

    private void receive(byte[] data) {
        SpannableStringBuilder spn = new SpannableStringBuilder();
        spn.append("receive " + data.length + " bytes\n");
        if(data.length > 0)
            spn.append(HexDump.dumpHexString(data)).append("\n");
        tv_rec.append(spn);
    }

    private void send_msg(String msg){
        if (!serialOpenFlag){
            Toast.makeText(SerialTestActivity.this, "No Select device", Toast.LENGTH_SHORT).show();
        }else {
            byte[] data = (msg + '\n').getBytes();
            try {
                usbSerialPort.write(data, WRITE_WAIT_MILLIS);
                Toast.makeText(SerialTestActivity.this, "Send msg success!", Toast.LENGTH_SHORT).show();
            }catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

//    private void disconnect() {
//        serialOpenFlag = false;
////        controlLines.stop();
//        if(usbIoManager != null) {
//            usbIoManager.setListener(null);
//            usbIoManager.stop();
//        }
//        usbIoManager = null;
//        try {
//            usbSerialPort.close();
//        } catch (IOException ignored) {}
//        usbSerialPort = null;
//    }

    @Override
    public void onClick(View v) {
//        int id = v.getId();
//        if (id == R.id.bt_open){
//            openSerial();
//        } else if (id == R.id.bt_refresh) {
//            refresh();
//        } else if (id == R.id.bt_send) {
//            String send_string = et_send.getText().toString();
//            send_msg(send_string);
//        }
    }
}