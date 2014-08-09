package com.speleo.soundsurvey;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.os.Bundle;
import android.app.Activity;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ToggleButton;
import android.util.Log;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;


public class MainActivity extends Activity {
	private static final int RECORDER_SAMPLERATE = 8000;
	private static final boolean ENCODE_MPEG4 = true;
	private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
	private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
	public static int bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING)*20;
	public static boolean recording = false;
	public static boolean isRecording = false;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i("SoundSurvey","SoundSurvey started!");
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    public static boolean listening = true;
        
    public void onEnableDisable(View view){
    	Log.i("SoundSurvey","onEnableDisable called");
    	ToggleButton t = (ToggleButton) findViewById(R.id.toggleButton1);
    	if (t.isChecked())
    	{
    		Log.i("SoundSurvey","ON");
    		listening = true;
    		startListening();
    		
    	}
    	else 
    	{
    		Log.i("SoundSurvey","OFF");
    		listening = false;
    	}	
    };
    
    static void startListening(){
    Thread listeningThread = new Thread(new Runnable() 
    {

    		public void run() {

    			listen();
    		}
    }, "Sound Survey Thread");
    
    listeningThread.start();
    
    }
       
    /*this is a thread, listens for sounds and starts recording if it does*/
    public static boolean listen(){
    	Log.i("SoundSurvey","Listening enabled");
    	/*AudioRecord allow audio raw audio data stream reading*/
    	AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
    			RECORDER_SAMPLERATE, RECORDER_CHANNELS,
    			RECORDER_AUDIO_ENCODING, bufferSize);
    	ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
    	recorder.startRecording();
		byte prev=0;
		ByteBuffer bb = ByteBuffer.allocate(2);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		int trigger = 300; // value that trigger recording. 100 is a low value
		int secondsAfterSilence=5; //continue recording after last trigger
    	int silenceCounter=secondsAfterSilence+1; //ugly but simple. This avoid loop to start like it was the first silence after sound
    	while (listening)
    	{
    		 /*fill buffer with raw audio data*/
    		 recorder.read(buffer, bufferSize);
    		 
    		 /*calculate root means square for values in buffer. Rms of PCM values is the average volume of the buffer*/
    		 int rms;
    		 double sum=0;
    		 byte values[] = buffer.array();
    		 for (int i=0 ; i<values.length; i++)
    		 {
    			//convert short to byte
    			 if (i%2 == 0)
    			 {
    				 prev = values[i];
    				 continue;
    			 } else {
    			     bb.put(prev);
    			     bb.put(values[i]);
    			     short shortVal = bb.getShort(0);
    			     bb.rewind();
    			     sum = sum + Math.pow(shortVal,2);
    			 }
    		 }
    		 rms = (int) Math.sqrt(sum/(values.length/2));
    		 Log.i("RMS:", ""+rms);
    		 
    		 if (rms>trigger) //the buffer average volume is bigger than trigger value
    		 {
    			//start recording if not, put silenceCounter to 0
    			 Log.d("status","recording...");
    			 recording = true; 
    			 silenceCounter=0;
    		 } 
    		 else 
    		 {
    			 /*Silence. Volume is under trigger value.*/
    			 /*if silenceCounter<secondsAfterSilence keep recording and increment silenceCounter  */
    			 if (silenceCounter<secondsAfterSilence) 
    			 {
    				 //recording is true
        			 Log.d("status","silence " + silenceCounter +" keep recording...");
    				 silenceCounter=silenceCounter+1;
    			 } 
    			 else    			 /*stop recording if silenceCounter>=secondsAfterSilence)*/
    			 {
    				 if (isRecording)
    				 {
    					 Log.i("status", "stop recording!");
    					 recording=false;    				 
    					 isRecording = false;
    					 /*Close output stuff*/
    					 try 
    					 {
    						 closeOutput();
    					 } catch (Exception e) {
    						 e.printStackTrace();
    					 }
    					 outFile = null;
    				 } 
    				 else
    				 {
    	    			 Log.d("status","do nothing");
    				 }
    			 }
    		 }
    		 
    		 if (recording)
    		 {
    			 isRecording = true;
    			 //Write buffer to file 
    				try {
    					/*write current buffer to the output file*/
    					output(buffer);
    				} catch (Exception e) {
    					e.printStackTrace();
    				}	 
    		 }	
    		 buffer.rewind();
    	} //END of thread while loop. User stopped recorder
    	//close file if it's still opened
    	if (outFile != null)
    	{
    		try {
    			closeOutput();
			  } catch (Exception e) {
				  e.printStackTrace();
			  }
    		 outFile = null;	 
    	}
    	recorder.stop();
    	recorder.release();
    	recorder = null;
    	return true;
    }
    
    static private File outFile = null;
    static MediaMuxer mux = null;
    static MediaFormat outputFormat = null;
    static int COMPRESSED_AUDIO_FILE_BIT_RATE = 320000; // 320kbps
    static final String COMPRESSED_AUDIO_FILE_MIME_TYPE = "audio/mp4a-latm";
    static MediaCodec codec;
    static ByteBuffer[] codecInputBuffers;
    static ByteBuffer[] codecOutputBuffers;
    static MediaCodec.BufferInfo outBuffInfo;
    static final int CODEC_TIMEOUT_IN_MS = 5000;
    static int audioTrackIdx = 0;
    static long startTime = System.nanoTime();
    
    public static void output(ByteBuffer buffer)
    {
    	//create new file if there isn't one open 
    	if (outFile == null) 
    	{
    		/*Get current timestamp to write it in filename*/
			Long tsLong = System.currentTimeMillis();
			String ts = tsLong.toString();	
			String filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + ts + "_8k16bitMono.mp4";
			try {
				outFile = new File(filePath);
				Log.i("IO","created a new file: " + filePath);
			} catch (Exception e) {
				e.printStackTrace();
			}
    	} 
    	else 
    	{
    		Log.d("IO","append data to existing file");
    	}
    	
    	if (ENCODE_MPEG4) 
    	{
    		/* create new MediaMuxer if it not exists.
    		 * MediaMuxer allow to encode PCM to MPEG-4 on the fly*/    	
    		if (mux == null)
    		{
    			try {
    				startTime = System.nanoTime();
    				Log.d("mux","creating new MediaMuxer");
    				mux = new MediaMuxer(outFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    				outputFormat = MediaFormat.createAudioFormat(COMPRESSED_AUDIO_FILE_MIME_TYPE,RECORDER_SAMPLERATE, 1);
    				outputFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
    				outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, COMPRESSED_AUDIO_FILE_BIT_RATE);
    				codec = MediaCodec.createEncoderByType(COMPRESSED_AUDIO_FILE_MIME_TYPE);
    				codec.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    				codec.start();
    				codecInputBuffers = codec.getInputBuffers(); // Note: Array of buffers
    				codecOutputBuffers = codec.getOutputBuffers();
    				outBuffInfo = new MediaCodec.BufferInfo();
    			} catch (Exception e){
    				e.printStackTrace();
    			}
    		}
    		//Write buffer 
    		buffer.rewind();
    		int inputBufferIndex = 0;
    		int puttedBytes=0;
    		int totalBytes=0;
//    		Log.d("buffer.hasRemaining()",""+buffer.hasRemaining());
//    		Log.d("inputBufferIndex",""+inputBufferIndex);
    		while (buffer.hasRemaining() && inputBufferIndex!=-1) 
    		{
    			
    			//get index of buffer to write
    			inputBufferIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_IN_MS);
    			if (inputBufferIndex >= 0) /*codec is ready to receive our data*/ 
    			{
    				puttedBytes=0;
    				/*fill codec buffer with data from input buffer*/ 
    				codecInputBuffers[inputBufferIndex].rewind();
    				while (codecInputBuffers[inputBufferIndex].hasRemaining() && buffer.hasRemaining())
    				{
    					codecInputBuffers[inputBufferIndex].put(buffer.get());
    					puttedBytes+=1;
    				}
    	    		codec.queueInputBuffer(inputBufferIndex, 0, puttedBytes,(long) System.nanoTime(),0);
//    				Log.d("queueInputBuffer","written " +puttedBytes);
//    				Log.d("buffer.hasRemaining()",""+buffer.hasRemaining());
//    				Log.d("inputBufferIndex",""+inputBufferIndex);
//    				Log.d("codecInputBuffers[inputBufferIndex].hasRemaining()",""+codecInputBuffers[inputBufferIndex].hasRemaining());
    				totalBytes = totalBytes + puttedBytes;
    			} 
    		}    		
    		Log.d("codec","Sent " + totalBytes +" bytes to codec encoder");
    		//get encoded output from codec
    		int outputBufferIndex = 0;
    		do 
    		{
    			/*take codec buffer*/ 
    			outputBufferIndex = codec.dequeueOutputBuffer(outBuffInfo,CODEC_TIMEOUT_IN_MS);
    			if (outputBufferIndex >= 0) 
    			{
    				ByteBuffer encodedData = codecOutputBuffers[outputBufferIndex];
    				encodedData.position(outBuffInfo.offset);
    				encodedData.limit(outBuffInfo.offset + outBuffInfo.size);
    				if ((outBuffInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0 && outBuffInfo.size != 0) {
    	    			Log.d("codec","(outBuffInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0 && outBuffInfo.size != 0)");
    					codec.releaseOutputBuffer(outputBufferIndex, false);
    				} else 
    				{
    					//Log.d("codec","writing data...");
    					mux.writeSampleData(audioTrackIdx, codecOutputBuffers[outputBufferIndex], outBuffInfo);
    					//Log.d("mux","written " + outBuffInfo.size);
    					codec.releaseOutputBuffer(outputBufferIndex, false);
    				}
    			} else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) 
    				{
    					outputFormat = codec.getOutputFormat();
                    	Log.d("muxer", "Output format changed - " + outputFormat);
                    	audioTrackIdx = mux.addTrack(outputFormat);
                    	mux.start();
    				} else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) 
    				{
    					Log.e("muxer", "Output buffers changed during encode!");
    				} else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) 
    				{
                    // NO OP
    				} else {
    					Log.e("muxwer", "Unknown return code from dequeueOutputBuffer - " + outputBufferIndex);
    				}    			
    		} while (outBuffInfo.flags != MediaCodec.BUFFER_FLAG_END_OF_STREAM && outputBufferIndex !=-1 );
    	} else {
    		/*no mpeg4 encoding 
    		 *TODO write raw data to the file*/    		
    	}
}

public static void closeOutput()
    {
    	int inputBufferIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_IN_MS);
    	if (inputBufferIndex >= 0) {
    		codec.queueInputBuffer(inputBufferIndex, 0,0,(long)System.nanoTime(),MediaCodec.BUFFER_FLAG_END_OF_STREAM);
    		Log.d("codec","write end of stream"); 
    	}
    	int outputBufferIndex = codec.dequeueOutputBuffer(outBuffInfo,CODEC_TIMEOUT_IN_MS);
    	if (outputBufferIndex >= 0) {
    	     // outputBuffer is ready to be processed or rendered.
    	     //Write to file 
    		 try {
    			 mux.writeSampleData(audioTrackIdx, codecOutputBuffers[outputBufferIndex], outBuffInfo);
    			 Log.d("mux","write data");
			 } catch (Exception e) {
				e.printStackTrace();
			 }	 
    	     codec.releaseOutputBuffer(outputBufferIndex, false);
    	   } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
               outputFormat = codec.getOutputFormat();
               Log.v("codec", "Output format changed - " + outputFormat);
               audioTrackIdx = mux.addTrack(outputFormat);
               mux.start();
           } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
    		   codecOutputBuffers = codec.getOutputBuffers();
    	   }
    	mux.stop();
        mux.release();
        mux=null;
        outFile=null;
    }
    
}
