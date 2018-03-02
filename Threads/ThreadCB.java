//Chiachen Ho, chiachen@email.sc.edu
package osp.Threads;
import java.util.Vector;
import java.util.Enumeration;
import osp.Utilities.*;
import osp.IFLModules.*;
import osp.Tasks.*;
import osp.EventEngine.*;
import osp.Hardware.*;
import osp.Devices.*;
import osp.Memory.*;
import osp.Resources.*;
import java.util.LinkedList;
import java.util.Queue;
import java.util.ArrayList;

/**
   This class is responsible for actions related to threads, including
   creating, killing, dispatching, resuming, and suspending threads.

   @OSPProject Threads
*/
public class ThreadCB extends IflThreadCB 
{
    
	 //private static GenericList readyQueue;
     private static ArrayList<ThreadCB> readyQueue ;
     private double lastCpuBurst;
     private double estimatedBurstTime;
     private double lastDispatch;



	/**
       The thread constructor. Must call 

       	   super();

       as its first statement.

       @OSPProject Threads
    */
    public ThreadCB()
    {
        // your code goes here
		super();

    }

    /**
       This method will be called once at the beginning of the
       simulation. The student can set up static variables here.
       
       @OSPProject Threads
    */
    public static void init()
    {
        // your code goes here
		readyQueue = new ArrayList<ThreadCB> ();
		

    }

    /** 
        Sets up a new thread and adds it to the given task. 
        The method must set the ready status 
        and attempt to add thread to task. If the latter fails 
        because there are already too many threads in this task, 
        so does this method, otherwise, the thread is appended 
        to the ready queue and dispatch() is called.

	The priority of the thread can be set using the getPriority/setPriority
	methods. However, OSP itself doesn't care what the actual value of
	the priority is. These methods are just provided in case priority
	scheduling is required.

	@return thread or null

        @OSPProject Threads
    */
    static public ThreadCB do_create(TaskCB task)
    {
        // your code goes here
		ThreadCB thread;
       
        if(MaxThreadsPerTask<=task.getThreadCount())      
        {
            ThreadCB.dispatch();
            return null;
        }

        thread = new ThreadCB();
        thread.lastCpuBurst=10;
        thread.estimatedBurstTime=10;                            
        thread.setPriority(task.getPriority());                                        
        thread.setTask(task);
        thread.setStatus(ThreadReady);		
        if(task.addThread(thread)==0)                     
        {
            ThreadCB.dispatch();
            return null;
        }
        readyQueue.add(thread);                          
        ThreadCB.dispatch();                                
        return thread;      

    }

    /** 
	Kills the specified thread. 

	The status must be set to ThreadKill, the thread must be
	removed from the task's list of threads and its pending IORBs
	must be purged from all device queues.
        
	If some thread was on the ready queue, it must removed, if the 
	thread was running, the processor becomes idle, and dispatch() 
	must be called to resume a waiting thread.
	
	@OSPProject Threads
    */
    public void do_kill()
    {
        // your code goes here
		if(getStatus()==ThreadReady){
			readyQueue.remove(this);
		}
		else if(getStatus()==ThreadRunning){
		ThreadCB thread = null;
                try
                {
                    thread = MMU.getPTBR().getTask().getCurrentThread();
                    if(this == thread)
                    {
                        MMU.setPTBR(null);
                        getTask().setCurrentThread(null);					
                    }
                }
                catch(NullPointerException e){}	
		}
		     
        getTask().removeThread(this);                                       
        setStatus(ThreadKill);                                              
        for(int i = 0; i<Device.getTableSize();i++)                        
        {
            Device.get(i).cancelPendingIO(this);
        }
        ResourceCB.giveupResources(this);                                    
        ThreadCB.dispatch();                                                
        if(getTask().getThreadCount() == 0)                                 
        {
            getTask().kill();
        }
    }

    /** Suspends the thread that is currenly on the processor on the 
        specified event. 

        Note that the thread being suspended doesn't need to be
        running. It can also be waiting for completion of a pagefault
        and be suspended on the IORB that is bringing the page in.
	
	Thread's status must be changed to ThreadWaiting or higher,
        the processor set to idle, the thread must be in the right
        waiting queue, and dispatch() must be called to give CPU
        control to some other thread.

	@param event - event on which to suspend this thread.

        @OSPProject Threads
    */
    public void do_suspend(Event event)
    {
        // your code goes here
		int status = getStatus();                                       
        if(status>=ThreadWaiting)                                       
        {
            setStatus(status+1);
        }
        else if(status == ThreadRunning)                                // #2
        {
            ThreadCB thread = null;
            try
            {
                thread = MMU.getPTBR().getTask().getCurrentThread();
                if(this==thread)
                {
                    MMU.setPTBR(null);
                    getTask().setCurrentThread(null);
                    setStatus(ThreadWaiting);
                    thread.lastCpuBurst=(double)HClock.get()-thread.lastDispatch;
                    thread.estimatedBurstTime=0.75*thread.lastCpuBurst+0.25*thread.estimatedBurstTime;                           
                }
            }
            catch(NullPointerException e){}              
        }
        if(!readyQueue.contains(this))
        {
            event.addThread(this);                                      
        }
        else
        {
            readyQueue.remove(this);
        } 
        ThreadCB.dispatch();   
    }

    /** Resumes the thread.
        
	Only a thread with the status ThreadWaiting or higher
	can be resumed.  The status must be set to ThreadReady or
	decremented, respectively.
	A ready thread should be placed on the ready queue.
	
	@OSPProject Threads
    */
    public void do_resume()
    {
        // your code goes here
		if(getStatus() < ThreadWaiting) {
            return;
        }
                           
        if(this.getStatus() == ThreadWaiting) {
            setStatus(ThreadReady);
        } else if (this.getStatus() > ThreadWaiting) {
            setStatus(getStatus()-1);
        }
              
        if (getStatus() == ThreadReady) {
            readyQueue.add(this);
        }        
        dispatch();

    }

    /** 
        Selects a thread from the run queue and dispatches it. 

        If there is just one theread ready to run, reschedule the thread 
        currently on the processor.

        In addition to setting the correct thread status it must
        update the PTBR.
	
	@return SUCCESS or FAILURE

        @OSPProject Threads
    */
    public static int do_dispatch()
    {
        // your code goes here
		ThreadCB thread = null;
               
        try
        {
            thread = MMU.getPTBR().getTask().getCurrentThread();    
        }
        catch(NullPointerException e){}
        
        if(thread!=null)                                          
        {
            double cpuBurstTime=(double)HClock.get()-thread.lastDispatch;
            for(ThreadCB tmp : readyQueue){    
                if(cpuBurstTime>tmp.estimatedBurstTime){
                    thread.getTask().setCurrentThread(null);
                    MMU.setPTBR(null);
                    thread.setStatus(ThreadReady);
                    readyQueue.add(thread);
                    thread.lastCpuBurst=(double)HClock.get()-thread.lastDispatch;
                    thread.estimatedBurstTime=0.75*thread.lastCpuBurst+0.25*thread.estimatedBurstTime;
                    if(readyQueue.isEmpty())                                    
                    {
                        MMU.setPTBR(null);
                        return FAILURE;
                    }       
                    else
                    {
                        double min=readyQueue.get(0).estimatedBurstTime;
                        for(ThreadCB tmp1 : readyQueue){
                            if(tmp1.estimatedBurstTime<min) {
                                min=tmp1.estimatedBurstTime;
                                thread=tmp1;
                            }
                        }
                        readyQueue.remove(thread);           
                        MMU.setPTBR(thread.getTask().getPageTable());           
                        thread.getTask().setCurrentThread(thread);              
                        thread.setStatus(ThreadRunning);
                        thread.lastDispatch=(double)HClock.get();                        
                    }              
                    break;
                }
            }
            
            
        }      
                                       
        return SUCCESS;   

    }

   
    public static void atError()
    {
        // your code goes here

    }

    /** Called by OSP after printing a warning message. The student
        can insert code here to print various tables and data
        structures in their state just after the warning happened.
        The body can be left empty, if this feature is not used.
       
        @OSPProject Threads
     */
    public static void atWarning()
    {
        // your code goes here

    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */

}

/*
      Feel free to add local classes to improve the readability of your code
*/
