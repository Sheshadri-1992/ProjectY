package com.mapreduce.tasktracker;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.protobuf.InvalidProtocolBufferException;
import com.mapreduce.client.Constants;
import com.mapreduce.misc.MapReduce.*;
import com.mapreduce.jobtracker.IJobTracker;
import com.mapreduce.jobtracker.MapInfo;

public class TTrackerDriver {

	
	 static int numMapThreads=5;
	 static int numRedThreads=5;
	 static int id;
	
	 static ArrayList<MapTaskStatus> mapTaskStatus;
	 static ArrayList<ReduceTaskStatus> redTaskStatus;
	 
	 static DataNodeLocation.Builder myLocation;
	 static ExecutorService executorMap ;
	 static ExecutorService executorReduce;
	 
	  public static void main(String[] args) {
	      
		 
		 id = Integer.parseInt(args[0]);
		 
		 myLocation.setIp(getMyIP());
		 myLocation.setPort(1099);
		 
		 mapTaskStatus = new ArrayList<>();
		 redTaskStatus = new ArrayList<>();
		 
		  executorMap = Executors.newFixedThreadPool(5);
		  executorReduce = Executors.newFixedThreadPool(5);
		 
		 sendHeartBeat();
		 
//		 ptional int32 taskTrackerId = 1;
//		  optional int32 numMapSlotsFree = 2;
//		  optional int32 numReduceSlotsFree = 3;
//		  repeated MapTaskStatus mapStatus = 4;
//		  repeated ReduceTaskStatus reduceStatus = 5;
//		  optional DataNodeLocation locations = 6; //addded
		 
		 
	  }

	 
	 public void sample()
	 {
		  ExecutorService executor = Executors.newFixedThreadPool(5);//creating a pool of 5 threads
	        for (int i = 0; i < 10; i++) {
	            Runnable worker = new WorkerThread("" + i);
	            executor.execute(worker);//calling execute method of ExecutorService
	          }
	        executor.shutdown();
	        while (!executor.isTerminated()) {   }

	        System.out.println("Finished all threads");
	 }
	 
	 
	 
	 
	 static void sendHeartBeat()
	{
		
		 final Registry registry = getJTRegistry();
		 
		 new Thread(new Runnable() {
             @Override
             public void run() {
            	 while(true)
            	 {
//	            		
            
         			try {
						IJobTracker jtStub=(IJobTracker) registry.lookup(Constants.JOB_TRACKER_IP);
						
						
						HeartBeatRequest.Builder req = HeartBeatRequest.newBuilder();
						req.setLocations(myLocation);
						req.addAllMapStatus(mapTaskStatus);
						req.addAllReduceStatus(redTaskStatus);
						req.setNumMapSlotsFree(numMapThreads);
						req.setNumReduceSlotsFree(numRedThreads);
						
						byte [] resAray =   jtStub.heartBeat(req.build().toByteArray());
						
						removeCompletedMapTask();
						removeCompletedRedTask();
						
						try {
							HeartBeatResponse res = HeartBeatResponse.parseFrom(resAray);
							
							if(res.getMapTasksList().size()!=0)
							{
								startMapTaskThread(res.getMapTasksList().get(0));
							}
							
							if(res.getReduceTasksList().size()!=0)
							{
								startReduceTaskThread(res.getReduceTasksList().get(0));
							}
							
						
							
							
						} catch (InvalidProtocolBufferException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						
						
						
						
						
					} catch (RemoteException | NotBoundException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}  // @Sheshadri : Name kept for binding
         			
            		 
            		 try {
						Thread.sleep(com.mapreduce.misc.Constants.HEART_BEAT_FREQ);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
            	 }
            	 
             }
         }).start();
		
		
	}
	 
	 
	 
	 private static  Registry getJTRegistry()
		{
			BufferedReader buff;
			String line="";
			try {
				buff = new BufferedReader(new FileReader(Constants.JT_CONF_FILE));  
				line=buff.readLine();
				buff.close();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
			
			String tokens[] = line.split(" ");
			
			Registry registry =null;
			try {
				registry = LocateRegistry.getRegistry(tokens[0],Integer.parseInt(tokens[1]));
				
			}catch(RemoteException e)
			{
				
			}
			
			return registry;
		}
	 
	 
//	 private static  Registry getNameNodeRegistry()
//		{
//			BufferedReader buff;
//			String line="";
//			try {
//				buff = new BufferedReader(new FileReader(Constants.NAMENODE_CONF));  
//				line=buff.readLine();
//				buff.close();
//			} catch (IOException e1) {
//				// TODO Auto-generated catch block
//				e1.printStackTrace();
//			}
//			
//			
//			String tokens[] = line.split(" ");
//			
//			Registry registry =null;
//			try {
//				registry = LocateRegistry.getRegistry(tokens[0],Integer.parseInt(tokens[1]));
//				
//			}catch(RemoteException e)
//			{
//				
//			}
//			
//			return registry;
//		}

	 
	 
	 static void startMapTaskThread(MapTaskInfo info)
	 {
		 MapTaskStatus.Builder status = MapTaskStatus.newBuilder();
		 status.setTaskId(info.getTaskId());
		 status.setJobId(info.getJobId());
		 status.setMapOutputFile(getMapOutFileName(info.getJobId(),info.getTaskId(),"map"));
		 status.setTaskCompleted(false);
		 
		 mapTaskStatus.add(status.build());
		 
		 Runnable worker = new MapThread(info);
		 executorMap.execute(worker);
	 }
	 
	 static void startReduceTaskThread(ReducerTaskInfo info)
	 {
		 
	 }
	 
	 
	 static String getMapOutFileName(Integer jobID,Integer taskID,String type)
	 {
		 return "job_"+jobID.toString()+"_map_"+taskID.toString();
	 }
	 
	 
	 static synchronized void updateMapStatus(MapTaskInfo info)
	 {
		 for(int i=0;i<mapTaskStatus.size();i++)
		 {
			 if(mapTaskStatus.get(i).getTaskId()==info.getTaskId() && mapTaskStatus.get(i).getJobId()==info.getJobId())
			 {
				 MapTaskStatus.Builder status = MapTaskStatus.newBuilder(mapTaskStatus.get(i));
				 status.setTaskCompleted(true);
				 
				 mapTaskStatus.set(i, status.build());
				 break;
			 }
		 }
	 }
	 
	 static synchronized void updateReduceStatus(ReducerTaskInfo info)
	 {
		 for(int i=0;i<redTaskStatus.size();i++)
		 {
			 if(redTaskStatus.get(i).getTaskId()==info.getTaskId() && redTaskStatus.get(i).getJobId()==info.getJobId())
			 {
				 ReduceTaskStatus.Builder status = ReduceTaskStatus.newBuilder(redTaskStatus.get(i));
				 status.setTaskCompleted(true);
				 
				 redTaskStatus.set(i, status.build());
				 break;
			 }
		 }
	 }
	 
	 static synchronized void removeCompletedMapTask()
	 {
		 for(int i=0;i<mapTaskStatus.size();i++)
		 {
			 if(mapTaskStatus.get(i).getTaskCompleted())
			 {
				 mapTaskStatus.remove(i);
				 i--;
			 }
		 }
	 }
	 
	 
	 static synchronized void removeCompletedRedTask()
	 {
		 for(int i=0;i<redTaskStatus.size();i++)
		 {
			 if(redTaskStatus.get(i).getTaskCompleted())
			 {
				 redTaskStatus.remove(i);
				 i--;
			 }
		 }
	 }
	 
	 

		public static String getMyIP()
		{
			String myIp=null;
			Enumeration<NetworkInterface> n = null;
			try {
				n = NetworkInterface.getNetworkInterfaces();
				
			} catch (SocketException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
		     for (; n.hasMoreElements();)
		     {
		             NetworkInterface e = n.nextElement();
//		             System.out.println("Interface: " + e.getName());
		             
		             
		             
		            	 Enumeration<InetAddress> a = e.getInetAddresses();
			             for (; a.hasMoreElements();)
			             {
			                     InetAddress addr = a.nextElement();
//			                     System.out.println("  " + addr.getHostAddress());
			                     if(e.getName().equals(com.mapreduce.misc.Constants.CONNECTIVITY))
			                     {
			                    	myIp = addr.getHostAddress(); 
			                     }
			             }

		             
		            	 
		             
		             
		     }
		     
		     return myIp;
		}

	 
}







class WorkerThread implements Runnable {
    private String message;
    public WorkerThread(String s){
        this.message=s;
    }
     public void run() {
        System.out.println(Thread.currentThread().getName()+" (Start) message = "+message);
        processmessage();//call processmessage method that sleeps the thread for 2 seconds
        System.out.println(Thread.currentThread().getName()+" (End)");//prints thread name
    }
    private void processmessage() {
        try {  Thread.sleep(2000);  } catch (InterruptedException e) { e.printStackTrace(); }
    }
}

