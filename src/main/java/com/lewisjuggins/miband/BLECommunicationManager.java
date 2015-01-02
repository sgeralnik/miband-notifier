package com.lewisjuggins.miband;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;
import com.lewisjuggins.miband.bluetooth.AsyncBluetoothGatt;

import java.util.Set;
import java.util.UUID;

/**
 * Created by Lewis on 01/01/15.
 */
public class BLECommunicationManager
{
	private String TAG = this.getClass().getSimpleName();

	/*
		Read as:    cc 01 f4 01 00 00 f4 01 f2 01 60 09
		Written as: cc 01 f4 01 00 00 f4 01 00 00 00 00
		connIntMin: 575ms
		connIntMax: 625ms
		latency: 0ms
		timeout: 5000ms
		connInt: 622ms
		advInt: 1500ms


		Read as:    27 00 31 00 00 00 f4 01 30 00 60 09
		Written as: 27 00 31 00 00 00 f4 01 00 00 00 00
		connIntMin: 48ms
		connIntMax: 61ms
		latency: 0ms
		timeout: 5000ms
		connInt: 60ms
		advInt: 1500ms

		Read as:    06 00 50 00 02 00 d0 07 63 00 60 09
		Written as:
		connIntMin: 7ms
		connIntMax: 100ms
		latency: 2ms
		timeout: 20000ms
		connInt: 123ms
		advInt: 1500ms
	 */

	public static final byte[] mLowLatencyLeParams = new byte[]{0x27, 0x00, 0x31, 0x00, 0x00, 0x00, (byte)0xf4, 0x01, 0x00, 0x00, 0x00, 0x00};

	public static final byte[] mHighLatencyLeParams = new byte[]{(byte)0xcc, 0x01, (byte)0xf4, 0x01, 0x00, 0x00, (byte)0xf4, 0x01, 0x00, 0x00, 0x00, 0x00};

	private int attempts = 0;

	private String mDeviceAddress;

	private AsyncBluetoothGatt mGatt;

	public boolean mDeviceConnected = false;

	private final Context mContext;

	public boolean mBluetoothAdapterStatus = false;

	public boolean setupComplete = false;

	private BluetoothDevice mBluetoothMi;

	private BluetoothGattCharacteristic mControlPointChar;


	public BLECommunicationManager(final Context context)
	{
		this.mContext = context;
		setupBluetooth();
	}

	public void setupBluetooth()
	{
		Log.d(TAG, "Initialising Bluetooth connection");

		if(BluetoothAdapter.getDefaultAdapter().isEnabled())
		{
			attempts += 1;
			final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			final Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

			for(BluetoothDevice pairedDevice : pairedDevices)
			{
				if(pairedDevice.getName().equals("MI") && pairedDevice.getAddress().startsWith(MiBandConstants.MAC_ADDRESS_FILTER))
				{
					mDeviceAddress = pairedDevice.getAddress();
				}
			}

			if(mDeviceAddress != null)
			{
				mBluetoothMi = mBluetoothAdapter.getRemoteDevice(mDeviceAddress);
				attempts = 0;
				setupComplete = true;
				mBluetoothAdapterStatus = true;

				Log.d(TAG, "Initialising Bluetooth connection complete");
			}
			else
			{
				//Wait 10 seconds and try again, sometimes the Bluetooth adapter takes a while.
				if(attempts <= 10)
				{
					try
					{
						Thread.sleep(10000);
					}
					catch(InterruptedException e)
					{
						e.printStackTrace();
					}
					setupBluetooth();
				}
			}
		}
	}

	public synchronized void connectGatt()
		throws MiBandConnectFailureException
	{
		Log.d(TAG, "Establishing connection to gatt");

		mGatt = new AsyncBluetoothGatt(mBluetoothMi, mContext, true);

		try
		{
			mGatt.connect().waitSafely(10000);
			mGatt.discoverServices().waitSafely(10000);
			setLowLatency();
		}
		catch(InterruptedException e)
		{
			Log.d(TAG, "Failed to connect to gatt");
			throw new MiBandConnectFailureException("Failed to connect");
		}

		Log.d(TAG, "Established connection to gatt");
	}

	public synchronized void disconnectGatt()
	{
		if(mGatt != null)
		{
			//setMediumLatency();
			mGatt.disconnect();
			mGatt.close();
		}
	}

	public synchronized void write(final BluetoothGattCharacteristic characteristic)
	{
		try
		{
			mGatt.writeCharacteristic(characteristic).waitSafely();
		}
		catch(InterruptedException e)
		{
			Log.i(TAG, e.toString());
			write(characteristic);
		}
	}

	public void setLowLatency()
	{
		try
		{
			final BluetoothGattCharacteristic characteristic = getCharacteristic(MiBandConstants.UUID_CHARACTERISTIC_LE_PARAMS);
			characteristic.setValue(mLowLatencyLeParams);
			mGatt.writeCharacteristic(characteristic).waitSafely();
		}
		catch(InterruptedException e)
		{
			Log.i(TAG, e.toString());
			setLowLatency();
		}
	}

	public void setHighLatency()
	{
		try
		{
			final BluetoothGattCharacteristic characteristic = getCharacteristic(MiBandConstants.UUID_CHARACTERISTIC_LE_PARAMS);
			characteristic.setValue(mHighLatencyLeParams);
			mGatt.writeCharacteristic(characteristic).waitSafely();
		}
		catch(InterruptedException e)
		{
			Log.i(TAG, e.toString());
			setLowLatency();
		}
	}

	private BluetoothGattService getMiliService()
	{
		return mGatt.getService(MiBandConstants.UUID_SERVICE_MILI_SERVICE);
	}

	public BluetoothGattCharacteristic getCharacteristic(UUID uuid)
	{
		if(MiBandConstants.UUID_CHARACTERISTIC_CONTROL_POINT.equals(uuid) && mControlPointChar != null)
		{
			return mControlPointChar;
		}
		else if(MiBandConstants.UUID_CHARACTERISTIC_CONTROL_POINT.equals(uuid) && mControlPointChar == null)
		{
			mControlPointChar = getMiliService().getCharacteristic(uuid);
			return mControlPointChar;
		}

		return getMiliService().getCharacteristic(uuid);
	}
}
