import { Component, OnInit } from '@angular/core';
import { BackendService } from '../backend.service';

@Component({
  selector: 'app-batch-samples',
  templateUrl: './batch-samples.component.html',
  styleUrls: ['./batch-samples.component.scss']
})
export class BatchSamplesComponent implements OnInit {

  constructor(private backend: BackendService) { }

  samples: any;
  batchId: string;

  ngOnInit(): void {
  }

  loadSamplesForBatch(batchId: string) {
    delete this.samples;
    this.batchId = batchId;
    this.backend.getSamplesForBatch(batchId)
      .subscribe(
        result => {
          this.samples = result;
        }
      )
  }

}
